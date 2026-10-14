package github.ponyhuang.gimi.data.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.JsonValue.Companion.from
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ContentBlockParam.Companion.ofDocument
import com.anthropic.models.messages.ContentBlockParam.Companion.ofImage
import com.anthropic.models.messages.ContentBlockParam.Companion.ofText
import com.anthropic.models.messages.ContentBlockParam.Companion.ofThinking
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolResult
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolUse
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.ThinkingConfigParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.ToolUseBlockParam
import com.anthropic.models.messages.UrlImageSource
import com.anthropic.models.messages.UrlPdfSource
import com.anthropic.models.messages.Usage
import com.anthropic.models.messages.WebSearchTool20250305
import com.fasterxml.jackson.core.type.TypeReference
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.serialization.jsonElementToAny
import com.google.adk.kt.tools.toJsonSchema
import com.google.adk.kt.types.UsageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.Base64
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.hours
import com.google.adk.kt.types.Tool as AdkTool

/**
 * Claude (Anthropic) Model — ADK [Model] bridge to the Anthropic Java SDK.
 *
 * @property providerBuiltInToolNames 当前服务以厂商原生形态执行的官方内置工具名
 * （如 Anthropic/MiniMax 的 `web_search`）；不在集合内的同名声明视为本地可执行函数，
 * 保持普通 tool 下发。
 *
 * @author pony
 * @date 2026/6/28
 */
open class Claude(
    override val name: String,
    private val client: AnthropicClient,
    private val providerBuiltInToolNames: Set<String> = emptySet(),
) : Model {

    companion object {
        private const val WEB_SEARCH_TOOL_ID: String = "web_search"
        private val JSON_MAPPER = jsonMapper()
        private val logger = LoggerFactory.getLogger(Claude::class)
    }

    protected open val defaultMaxTokens: Long = 8196L

    protected open fun resolveMaxTokens(request: LlmRequest): Long =
        request.config.maxOutputTokens?.toLong() ?: defaultMaxTokens

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val params = buildMessageCreateParams(request)

        if (stream) {
            logger.debug { "Sending streaming request to Claude model ${params.model()}" }
            processStreamingResponse(params)
        } else {
            logger.debug { "Sending request to Claude model ${params.model()}" }
            val message = try {
                client.messages().create(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnthropicException) {
                emit(mapToErrorResponse(e))
                return@flow
            }
            logger.debug { "Claude response: $message" }
            emit(message.toLlmResponse())
        }
    }

    protected open fun buildMessageCreateParams(request: LlmRequest): MessageCreateParams {
        logger.debug { "LLmRequest: $request" }
        val modelName = request.model?.name ?: name
        val systemInstruction = request.config.systemInstruction?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("\n")
            .orEmpty()
        // prompt cache 断点：命中时后续轮次直接复用前缀，省掉重复的输入 token。
        val cacheControl = promptCacheControl(modelName, request)

        val messages = request.contents
            .normalizeForAnthropic()
            .stream()
            .map { contentToAnthropicMessageParam(it) }
            .collect(Collectors.toList())

        val paramsBuilder = MessageCreateParams.builder()
            .model(modelName)
            .messages(if (cacheControl == null) messages else messages.withCacheBreakpoint(cacheControl))
            .maxTokens(resolveMaxTokens(request))

        if (systemInstruction.isNotBlank() && cacheControl != null) {
            paramsBuilder.systemOfTextBlockParams(
                listOf(TextBlockParam.builder().text(systemInstruction).cacheControl(cacheControl).build()),
            )
        } else {
            paramsBuilder.system(systemInstruction)
        }

        request.config.thinkingConfig?.let { thinkingConfig ->
            if (thinkingConfig.includeThoughts == false) {
                paramsBuilder.thinking(
                    ThinkingConfigParam.ofDisabled(
                        ThinkingConfigDisabled.builder().build()
                    )
                )
            } else {
                thinkingConfig.thinkingLevel?.let { level ->
                    if (usesAdaptiveThinking(modelName)) {
                        // 4.5 起只接受 adaptive；带上 summarized 才会把思考内容回传。
                        paramsBuilder.thinking(
                            ThinkingConfigParam.ofAdaptive(
                                ThinkingConfigAdaptive.builder()
                                    .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
                                    .build(),
                            ),
                        )
                    } else {
                        val budget = minOf(level.toClaudeThinkingBudget(), resolveMaxTokens(request) - 1)
                        if (budget >= MIN_CLAUDE_THINKING_BUDGET) {
                            paramsBuilder.thinking(
                                ThinkingConfigParam.ofEnabled(
                                    ThinkingConfigEnabled.builder().budgetTokens(budget).build(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        toAnthropicTools(request.config.tools)
            .takeIf { it.isNotEmpty() }
            ?.let { paramsBuilder.tools(markLastToolForCache(it, cacheControl)) }

        structuredOutputFormat(request)?.let { format ->
            paramsBuilder.outputConfig(OutputConfig.builder().format(format).build())
        }

        postProcessParams(paramsBuilder, request)

        return paramsBuilder.build()
    }

    /**
     * 本次请求的 prompt cache 断点，`null` 表示不打断点。
     *
     * 只在原生 Claude 家族启用：其他厂商的 Anthropic 兼容端点未必接受 `cache_control`，
     * 不替它们做假设。应用若通过 ADK 配置了 [LlmRequest.cacheConfig]，则其最小 token 门槛
     * 与一小时 TTL 一并生效。
     */
    private fun promptCacheControl(modelName: String, request: LlmRequest): CacheControlEphemeral? {
        if (!modelName.contains("claude", ignoreCase = true)) return null
        val cache = request.cacheConfig
        if (cache != null) {
            val previousPromptTokens = request.cacheableContentsTokenCount
            if (previousPromptTokens != null && previousPromptTokens < cache.minTokens) return null
        }
        return CacheControlEphemeral.builder()
            .apply {
                // 一小时是 Claude 保留前缀的上限；更短的寿命走默认的 5 分钟。
                if (cache != null && cache.ttl >= 1.hours) ttl(CacheControlEphemeral.Ttl.TTL_1H)
            }
            .build()
    }

    /** 只标记 Claude 自家 tool 的最后一项；内置工具没有可标记的 cache 断点。 */
    private fun markLastToolForCache(
        tools: List<ToolUnion>,
        cacheControl: CacheControlEphemeral?,
    ): List<ToolUnion> {
        if (cacheControl == null) return tools
        val last = tools.last()
        if (!last.isTool()) return tools
        return tools.dropLast(1) + ToolUnion.ofTool(
            last.asTool().toBuilder().cacheControl(cacheControl).build(),
        )
    }

    /**
     * 把 ADK 的响应 schema 映射为 Claude 的结构化 JSON 输出格式。
     *
     * 仅在请求显式要求 `application/json` 且带 schema 时下发；Claude 的 schema 不接受
     * 长度/数值边界等关键字，且对象必须禁止未声明字段，这里按官方适配规则改写。
     */
    @OptIn(FrameworkInternalApi::class)
    private fun structuredOutputFormat(request: LlmRequest): JsonOutputFormat? {
        if (!request.config.responseMimeType.equals("application/json", ignoreCase = true)) return null
        val schema = request.config.responseSchema ?: return null
        val adapted = jsonElementToAny(schema.toJsonSchema()).toClaudeOutputSchema() as? Map<*, *>
            ?: return null
        return JsonOutputFormat.builder()
            .schema(
                JsonOutputFormat.Schema.builder()
                    .putAllAdditionalProperties(
                        adapted.entries.associate { (key, value) -> key.toString() to from(value) },
                    )
                    .build(),
            )
            .build()
    }

    /**
     * Composition hook invoked last inside [buildMessageCreateParams], just before `.build()`.
     * Override to attach vendor-specific params (e.g. `temperature`, `top_p`, custom headers) without reimplementing the builder.
     */
    protected open fun postProcessParams(
        builder: MessageCreateParams.Builder,
        request: LlmRequest,
    ) {
        // Default: no-op.
    }

    protected open suspend fun FlowCollector<LlmResponse>.processStreamingResponse(params: MessageCreateParams) {
        val accumulator = MessageAccumulator.create()

        try {
            client.messages().createStreaming(params).use { streamResponse ->
                val it = streamResponse.stream().iterator()
                while (it.hasNext()) {
                    val event = it.next()
                    accumulator.accumulate(event)
                    event.streamingPartOrNull()?.let { part ->
                        emitStreamingPart(part)
                    }
                }
            }

            val message = accumulator.message()
            logger.debug { "Claude streaming complete: $message" }
            emit(message.toLlmResponse())

        } catch (e: CancellationException) {
            throw e
        } catch (e: AnthropicException) {
            logger.error(e) { "Error processing Claude streaming response" }
            emit(mapToErrorResponse(e))
        }
    }

    protected open fun RawMessageStreamEvent.streamingPartOrNull(): Part? {
        if (!isContentBlockDelta()) return null
        val delta = asContentBlockDelta().delta()
        return when {
            delta.isText() -> Part(text = delta.asText().text(), thought = false)
            delta.isThinking() -> Part(text = delta.asThinking().thinking(), thought = true)
            else -> null
        }
    }

    protected open suspend fun FlowCollector<LlmResponse>.emitStreamingPart(part: Part) {
        emit(
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(part)),
                partial = true
            )
        )
    }

    protected open fun Message.toLlmResponse(): LlmResponse = LlmResponse(
        content = Content(
            role = Role.MODEL,
            parts = content().asSequence()
                .mapNotNull { it.toPart() }
                .toList()
        ),
        usageMetadata = usage().toUsageMetadata()
    )

    protected open fun Usage.toUsageMetadata() = UsageMetadata(
        promptTokenCount = inputTokens().toInt(),
        candidatesTokenCount = outputTokens().toInt(),
        totalTokenCount = inputTokens().toInt() + outputTokens().toInt()
    )

    protected open fun ContentBlock.toPart(): Part? {
        return when {
            isText() -> Part(text = asText().text())
            isToolUse() -> Part(
                functionCall = FunctionCall(
                    id = asToolUse().id(),
                    name = asToolUse().name(),
                    args = asToolUse()._input().convert(
                        object : TypeReference<Map<String, Any?>>() {}
                    ) ?: emptyMap()
                )
            )

            isThinking() -> Part(
                text = asThinking().thinking(),
                thought = true,
                thoughtSignature = asThinking().signature().toByteArray(Charsets.UTF_8)
            )

            else -> null
        }
    }

    protected open fun contentToAnthropicMessageParam(content: Content) = MessageParam.builder()
        .role(toClaudeRole(content.role.toString()))
        .contentOfBlockParams(
            content.parts.stream()
                .map { it.toContentBlockParam() }
                .collect(Collectors.toList())
        )
        .build()

    protected open fun Part.toContentBlockParam(): ContentBlockParam {
        if (thought == true) {
            return ofThinking(
                ThinkingBlockParam.builder()
                    .thinking(text ?: "")
                    .signature(thoughtSignature?.toString(Charsets.UTF_8) ?: "")
                    .build()
            )
        }

        text?.let { return ofText(TextBlockParam.builder().text(it).build()) }

        inlineData?.let { blob ->
            val mimeType =
                blob.mimeType
                    ?: throw UnsupportedOperationException("Inline data MIME type is missing")
            val data = blob.data ?: throw UnsupportedOperationException("Inline data is missing")
            val base64 = Base64.getEncoder().encodeToString(data)
            return when {
                isSupportedImageMimeType(mimeType) ->
                    ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .mediaType(Base64ImageSource.MediaType.of(mimeType))
                                    .data(base64)
                                    .build()
                            )
                            .build()
                    )

                isSupportedDocumentMimeType(mimeType) ->
                    ofDocument(
                        DocumentBlockParam.builder()
                            .source(Base64PdfSource.builder().data(base64).build())
                            .build()
                    )

                else -> throw UnsupportedOperationException(
                    "Unsupported inline data MIME type: $mimeType"
                )
            }
        }

        fileData?.let { blob ->
            val mimeType =
                blob.mimeType
                    ?: throw UnsupportedOperationException("FileData MIME type is missing")
            val uri = blob.fileUri ?: throw UnsupportedOperationException("FileData Uri is missing")
            require(uri.isHttpReference()) { "Unsupported remote file reference: $uri" }
            return when {
                isSupportedImageMimeType(mimeType) ->
                    ofImage(
                        ImageBlockParam.builder()
                            .source(
                                UrlImageSource.builder().url(uri).build()
                            )
                            .build()
                    )

                isSupportedDocumentMimeType(mimeType) ->
                    ofDocument(
                        DocumentBlockParam.builder()
                            .source(
                                UrlPdfSource.builder().url(uri).build()
                            )
                            .build()
                    )

                else -> throw UnsupportedOperationException(
                    "Unsupported inline data MIME type: $mimeType"
                )
            }
        }

        functionCall?.let { fc ->
            return ofToolUse(
                ToolUseBlockParam.builder()
                    .id(fc.id.toString())
                    .name(fc.name)
                    .type(from("tool_use"))
                    .input(fc.toAnthropicToolUseInput())
                    .build()
            )
        }

        functionResponse?.let { fr ->
            val response = fr.response
            val result = response["result"]
                ?.let { JSON_MAPPER.writeValueAsString(it) }
                ?: JSON_MAPPER.writeValueAsString(response)
            return ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(fr.id.toString())
                    .content(result)
                    .isError(defaultToolResultIsError())
                    .build()
            )
        }
        throw UnsupportedOperationException("Not supported yet. $this")
    }

    private fun String.isHttpReference(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    protected open val defaultImageMimePrefix: String = "image/"

    protected open fun isSupportedImageMimeType(mimeType: String): Boolean =
        mimeType.startsWith(defaultImageMimePrefix)

    protected open fun isSupportedDocumentMimeType(mimeType: String): Boolean =
        mimeType == "application/pdf"

    protected open fun defaultToolResultIsError(): Boolean = false

    protected open fun toClaudeRole(role: String) = when (role) {
        "model", "assistant" -> MessageParam.Role.ASSISTANT
        else -> MessageParam.Role.USER
    }

    protected open fun FunctionDeclaration.toAnthropicTool(): Tool {
        // ADK represents a parameterless function with `parameters == null`.
        // Anthropic still requires `input_schema` to be a JSON object, so its
        // `properties` member must be an empty object rather than JSON null.
        val properties = Tool.InputSchema.Properties.builder()
            .additionalProperties(
                parameters?.properties.orEmpty().mapValues { (_, schema) ->
                    from(schema.toClaudeParameters())
                }
            )
            .build()
        val inputSchema = Tool.InputSchema.builder()
            .properties(properties)
            .required(parameters?.required.orEmpty())
            .build()

        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(inputSchema)
            .build()
    }

    protected open fun toAnthropicTools(tools: List<AdkTool>?): List<ToolUnion> =
        tools.orEmpty()
            .flatMap { it.functionDeclarations.orEmpty() }
            .map { declaration ->
                when (declaration.name) {
                    // 仅当当前服务把 web_search 声明为厂商内置工具时才转换 wire 形态；
                    // 同名真实函数（GLM 搜索、MCP 工具）保持普通 tool 下发。
                    WEB_SEARCH_TOOL_ID ->
                        if (WEB_SEARCH_TOOL_ID in providerBuiltInToolNames) {
                            ToolUnion.ofWebSearchTool20250305(
                                WebSearchTool20250305.builder().build(),
                            )
                        } else {
                            ToolUnion.ofTool(declaration.toAnthropicTool())
                        }

                    else -> ToolUnion.ofTool(declaration.toAnthropicTool())
                }
            }

    protected open fun Schema.toClaudeParameters(): Map<String, JsonValue> {
        val result = mutableMapOf<String, JsonValue>()
        type?.let { result["type"] = from(it.name.lowercase()) }
        description?.let { result["description"] = from(it) }
        properties?.let {
            result["properties"] = from(it.mapValues { (_, s) -> s.toClaudeParameters() })
        }
        required?.let { result["required"] = from(it) }
        items?.let { result["items"] = from(it.toClaudeParameters()) }
        enum?.let { result["enum"] = from(it) }
        return result
    }

    protected open fun mapToErrorResponse(e: Exception) = LlmResponse(
        finishReason = if (e is AnthropicServiceException) FinishReason.SAFETY
        else FinishReason.FINISH_REASON_UNSPECIFIED,
        errorMessage = if (e is AnthropicServiceException) "${e.statusCode()}: ${e.message}"
        else e.message ?: e::class.simpleName ?: "Unknown error",
    )
}

private const val MIN_CLAUDE_THINKING_BUDGET: Long = 1024L

/** Claude 使用 extended-thinking 的 token budget 表示同一套四档会话推理强度。 */
private fun ThinkingLevel.toClaudeThinkingBudget(): Long = when (this) {
    ThinkingLevel.MINIMAL -> 1024L
    ThinkingLevel.LOW -> 2048L
    ThinkingLevel.MEDIUM -> 4096L
    ThinkingLevel.HIGH, ThinkingLevel.THINKING_LEVEL_UNSPECIFIED -> 6144L
}

/**
 * 该模型是否只接受 adaptive thinking。
 *
 * Claude 4.5 起用 adaptive 取代固定 budget；按模型名的版本号判断，未知命名一律按旧的
 * budget 语义处理，避免对不认识的端点发出它可能拒绝的配置。
 */
private val CLAUDE_MODEL_VERSION = Regex("""claude-(?:[a-z]+-)?(\d+)(?:[-.](\d+))?""", RegexOption.IGNORE_CASE)

private fun usesAdaptiveThinking(modelName: String): Boolean {
    val match = CLAUDE_MODEL_VERSION.find(modelName) ?: return false
    val major = match.groupValues[1].toIntOrNull() ?: return false
    val minor = match.groupValues[2].toIntOrNull() ?: 0
    return major > 4 || (major == 4 && minor >= 5)
}

/**
 * 把最后一个可缓存的块标上 cache 断点，让下一轮从缓存里读已经形成的对话前缀。
 *
 * thinking 块不可标记；整条消息都不可标记时继续向前找。
 */
private fun List<MessageParam>.withCacheBreakpoint(
    cacheControl: CacheControlEphemeral,
): List<MessageParam> {
    for (i in indices.reversed()) {
        val blocks = this[i].content().asBlockParams()
        for (j in blocks.indices.reversed()) {
            val marked = blocks[j].withCacheControl(cacheControl) ?: continue
            val message = this[i].toBuilder()
                .contentOfBlockParams(blocks.toMutableList().apply { set(j, marked) })
            return toMutableList().apply { set(i, message.build()) }
        }
    }
    return this
}

/** 该块带上 cache 断点；thinking 块不能标记，返回 null 继续向前找。 */
private fun ContentBlockParam.withCacheControl(
    cacheControl: CacheControlEphemeral,
): ContentBlockParam? = when {
    isText() -> ofText(asText().toBuilder().cacheControl(cacheControl).build())
    isImage() -> ofImage(asImage().toBuilder().cacheControl(cacheControl).build())
    isDocument() -> ofDocument(asDocument().toBuilder().cacheControl(cacheControl).build())
    isToolUse() -> ofToolUse(asToolUse().toBuilder().cacheControl(cacheControl).build())
    isToolResult() -> ofToolResult(asToolResult().toBuilder().cacheControl(cacheControl).build())
    else -> null
}

/** Claude 结构化输出拒绝的 schema 关键字，改写到 description 里提示模型。 */
private val REJECTED_SCHEMA_KEYWORDS = setOf(
    "minimum",
    "maximum",
    "minLength",
    "maxLength",
    "maxItems",
    "minProperties",
    "maxProperties",
)

/** Claude 结构化输出支持的字符串格式；其余格式声明必须移除。 */
private val SUPPORTED_STRING_FORMATS = setOf(
    "date-time", "time", "date", "duration", "email", "hostname", "uri", "ipv4", "ipv6", "uuid",
)

/**
 * 按 Claude 的结构化输出规则改写 JSON Schema：对象禁止未声明字段、被拒关键字降级进
 * description、`nullable` 改写成显式的 null 选项。
 */
private fun Any?.toClaudeOutputSchema(): Any? {
    if (this !is Map<*, *>) return this
    val rejected = mutableListOf<String>()
    val schema = buildMap {
        for ((key, value) in this@toClaudeOutputSchema) {
            if (key == "nullable") continue
            if (isRejectedKeyword(key, value)) {
                rejected += "$key: $value"
                continue
            }
            this[key.toString()] = when (key) {
                "properties" -> (value as? Map<*, *>).orEmpty().mapValues { it.value.toClaudeOutputSchema() }
                "items" -> value.toClaudeOutputSchema()
                "anyOf" -> (value as? List<*>).orEmpty().map { it.toClaudeOutputSchema() }
                else -> value
            }
        }
        if (rejected.isNotEmpty()) {
            val note = rejected.joinToString(", ", prefix = "(", postfix = ")")
            this["description"] = listOfNotNull(this["description"], note).joinToString(" ")
        }
        if (this["type"] == "object") this["additionalProperties"] = false
    }
    return if (this["nullable"] == true) schema.allowingNull() else schema
}

/** 该 schema 再加上显式的 null 选项。 */
private fun Map<String, Any?>.allowingNull(): Map<String, Any?> {
    val type = this["type"]
    val enum = this["enum"] as? List<*>
    val anyOf = this["anyOf"] as? List<*>
    return when {
        // 枚举与类型列表不能共存，枚举本身已经限定了取值。
        enum != null -> this - "type" + ("enum" to (enum + null))
        type is String -> this + ("type" to listOf(type, "null"))
        anyOf != null -> this + ("anyOf" to (anyOf + mapOf("type" to "null")))
        else -> this
    }
}

private fun isRejectedKeyword(key: Any?, value: Any?): Boolean = when (key) {
    in REJECTED_SCHEMA_KEYWORDS -> true
    "minItems" -> (value as? Number)?.toInt()?.let { it > 1 } == true
    "format" -> value !in SUPPORTED_STRING_FORMATS
    else -> false
}


internal fun FunctionCall.toAnthropicToolUseInput(): ToolUseBlockParam.Input =
    ToolUseBlockParam.Input.builder()
        .additionalProperties(
            args.mapValues { (_, value) -> from(value) }
        )
        .build()

internal fun List<Content>.normalizeForAnthropic(): List<Content> {
    val merged = mutableListOf<Content>()
    for (content in this) {
        if (content.parts.isEmpty()) continue
        val role = content.role.toAnthropicContentRole()
        val previous = merged.lastOrNull()
        if (previous?.role == role) {
            merged[merged.lastIndex] = previous.copy(parts = previous.parts + content.parts)
        } else {
            merged += content.copy(role = role)
        }
    }

    return merged.map { content ->
        if (content.role != Role.USER) return@map content
        val (toolResults, otherParts) = content.parts.partition { it.functionResponse != null }
        content.copy(parts = toolResults + otherParts)
    }
}

private fun String?.toAnthropicContentRole(): String = when (this) {
    Role.MODEL, "assistant" -> Role.MODEL
    else -> Role.USER
}
