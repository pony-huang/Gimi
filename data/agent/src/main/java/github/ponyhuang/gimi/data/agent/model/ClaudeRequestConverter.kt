package github.ponyhuang.gimi.data.agent.model

import com.anthropic.core.JsonValue
import com.anthropic.core.JsonValue.Companion.from
import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ContentBlockParam.Companion.ofDocument
import com.anthropic.models.messages.ContentBlockParam.Companion.ofImage
import com.anthropic.models.messages.ContentBlockParam.Companion.ofText
import com.anthropic.models.messages.ContentBlockParam.Companion.ofThinking
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolResult
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolUse
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.RedactedThinkingBlockParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.ToolUseBlockParam
import com.anthropic.models.messages.UrlImageSource
import com.anthropic.models.messages.UrlPdfSource
import com.anthropic.models.messages.WebSearchTool20250305
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.serialization.jsonElementToAny
import com.google.adk.kt.tools.toJsonSchema
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import java.util.Base64
import kotlin.time.Duration.Companion.hours
import com.google.adk.kt.types.Tool as AdkTool
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds

/** 纯请求转换：处理历史、媒体、普通工具、缓存及 schema；传输与产品思考规则分别维护。 */
internal class ClaudeRequestConverter(private val name: String, private val defaultMaxTokens: Int) {
    private val jsonMapper = jsonMapper()

    fun convert(request: LlmRequest): MessageCreateParams {
        val modelName = request.model?.name ?: name
        val maxTokens = request.config.maxOutputTokens ?: defaultMaxTokens
        require(maxTokens > 0) { "Claude maxOutputTokens must be positive" }
        val thinking = ClaudeThinkingPolicy.resolve(modelName, request.config.thinkingConfig, maxTokens)
        val cache = promptCacheControl(modelName, request)
        val messages = request.contents.normalizeForAnthropic().mapNotNull(::contentToAnthropicMessageParam)
        val builder = MessageCreateParams.builder().model(modelName).maxTokens(maxTokens.toLong())
            .messages(if (cache == null) messages else messages.withCacheBreakpoint(cache))
        val system = request.config.systemInstruction?.parts?.mapNotNull { it.text }?.joinToString("\n").orEmpty()
        if (system.isNotBlank()) {
            if (cache == null) builder.system(system)
            else builder.systemOfTextBlockParams(listOf(TextBlockParam.builder().text(system).cacheControl(cache).build()))
        }
        toAnthropicTools(request.config.tools).takeIf { it.isNotEmpty() }?.let {
            builder.tools(markLastToolForCache(it, cache))
        }
        thinking.thinking?.let { builder.thinking(it) }
        val format = structuredOutputFormat(request)
        if (thinking.effort != null || format != null) {
            builder.outputConfig(OutputConfig.builder().apply {
                thinking.effort?.let { effort(it) }
                format?.let { format(it) }
            }.build())
        }
        // 与官方 JVM 一致：思考或 effort 启用时不发送可能被模型拒绝的采样参数。
        if (!thinking.isEnabled) {
            @Suppress("DEPRECATION")
            request.config.let { config ->
                config.temperature?.let { builder.temperature(it.toDouble()) }
                config.topP?.let { builder.topP(it.toDouble()) }
                config.topK?.let { builder.topK(it.toLong()) }
            }
        }
        request.config.stopSequences?.let { builder.stopSequences(it) }
        return builder.build()
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

    /** 普通工具列表的最后一项作为缓存前缀断点；厂商原生工具没有可标记的 cache 字段。 */
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

    private fun contentToAnthropicMessageParam(content: Content): MessageParam? {
        val blocks = content.parts.mapNotNull { it.toContentBlockParam() }
        if (blocks.isEmpty()) return null
        return MessageParam.builder()
            .role(toClaudeRole(content.role.toString()))
            .contentOfBlockParams(blocks)
            .build()
    }

    private fun Part.toContentBlockParam(): ContentBlockParam? {
        if (thought == true) {
            // 历史里的思考必须带服务签名；流式中间片段或其他模型的无签名思考不能重放。
            val signature = thoughtSignature?.toString(Charsets.UTF_8)?.ifEmpty { null } ?: return null
            val thinking = text ?: return ContentBlockParam.ofRedactedThinking(
                RedactedThinkingBlockParam.builder().data(signature).build(),
            )
            return ofThinking(ThinkingBlockParam.builder().thinking(thinking).signature(signature).build())
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
                ?.let { jsonMapper.writeValueAsString(it) }
                ?: jsonMapper.writeValueAsString(response)
            return ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(fr.id.toString())
                    .content(result)
                    .isError(response["error"] != null)
                    .build()
            )
        }
        throw UnsupportedOperationException("Not supported yet. $this")
    }

    private fun String.isHttpReference(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun isSupportedImageMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/")

    private fun isSupportedDocumentMimeType(mimeType: String): Boolean =
        mimeType == "application/pdf"

    private fun toClaudeRole(role: String) = when (role) {
        "model", "assistant" -> MessageParam.Role.ASSISTANT
        else -> MessageParam.Role.USER
    }

    private fun FunctionDeclaration.toAnthropicTool(): Tool {
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

    private fun toAnthropicTools(tools: List<AdkTool>?): List<ToolUnion> =
        tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
            .map(::toAnthropicToolUnion)

    /**
     * 厂商远端执行的声明必须保留厂商 wire 形态：下成普通函数会让模型把它当成客户端函数
     * 并要求本地执行，而本地运行时没有可执行的实现。
     */
    private fun toAnthropicToolUnion(declaration: FunctionDeclaration): ToolUnion =
        nativeProviderTool(declaration.name) ?: ToolUnion.ofTool(declaration.toAnthropicTool())

    /**
     * 已支持的厂商原生声明；本地声明名不在这里的（普通函数、MCP 工具、GLM 本地搜索）
     * 一律按普通函数下发。
     */
    private fun nativeProviderTool(declarationName: String): ToolUnion? = when (declarationName) {
        OfficialToolIds.PROVIDER_WEB_SEARCH ->
            ToolUnion.ofWebSearchTool20250305(WebSearchTool20250305.builder().build())

        else -> null
    }

    private fun Schema.toClaudeParameters(): Map<String, JsonValue> {
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

/**
 * 合并消息并闭合工具调用，满足 Anthropic 的相邻 tool_use / tool_result 约束。
 *
 * 取消会保留已提交的调用、但不一定留下结果；只在请求副本中补充“结果未知”的错误，
 * 不重跑工具，也不修改会话日志，避免影响 ADK 按 invocationId 恢复原执行。
 */
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

    val normalized = merged.mapIndexed { index, content ->
        if (content.role != Role.USER) return@mapIndexed content
        val (toolResults, otherParts) = content.parts.partition { it.functionResponse != null }
        val calls = merged.getOrNull(index - 1)?.parts.orEmpty().mapNotNull { it.functionCall }
        content.copy(parts = toolResults + calls.interruptedToolResults(toolResults) + otherParts)
    }.toMutableList()
    // 历史可能直接结束在工具调用上；错误结果仍须独占紧接着的 user 消息。
    val trailingCalls = merged.lastOrNull()?.takeIf { it.role == Role.MODEL }
        ?.parts.orEmpty().mapNotNull { it.functionCall }
    val trailingResults = trailingCalls.interruptedToolResults(emptyList())
    if (trailingResults.isNotEmpty()) {
        normalized += Content(role = Role.USER, parts = trailingResults)
    }
    return normalized
}

/** 已有真实结果保持原样，只为本轮未配对的调用提供错误占位。 */
private fun List<FunctionCall>.interruptedToolResults(results: List<Part>): List<Part> {
    val answeredIds = results.mapNotNull { it.functionResponse?.id }.toSet()
    return filter { it.id !in answeredIds }.map { call ->
        Part(functionResponse = FunctionResponse(
            id = call.id,
            name = call.name,
            response = mapOf(
                "error" to "Tool execution was interrupted before a result was available. The outcome is unknown.",
            ),
        ))
    }
}

private fun String?.toAnthropicContentRole(): String = when (this) {
    Role.MODEL, "assistant" -> Role.MODEL
    else -> Role.USER
}
