package github.ponyhuang.gimi.agent.model

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.JsonValue.Companion.from
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
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
import com.anthropic.models.messages.ThinkingConfigParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.ToolUseBlockParam
import com.anthropic.models.messages.UrlImageSource
import com.anthropic.models.messages.UrlPdfSource
import com.anthropic.models.messages.Usage
import com.anthropic.models.messages.WebSearchTool20250305
import com.fasterxml.jackson.core.type.TypeReference
import com.google.adk.kt.logging.LoggerFactory
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
import com.google.adk.kt.types.UsageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.Base64
import java.io.File
import java.util.stream.Collectors
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
            } catch (e: AnthropicServiceException) {
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

        val messages = request.contents
            .normalizeForAnthropic()
            .stream()
            .map { contentToAnthropicMessageParam(it) }
            .collect(Collectors.toList())

        val paramsBuilder = MessageCreateParams.builder()
            .model(modelName)
            .system(systemInstruction)
            .messages(messages)
            .maxTokens(resolveMaxTokens(request))

        request.config.thinkingConfig?.let {
            if (it.includeThoughts == false) {
                paramsBuilder.thinking(
                    ThinkingConfigParam.ofDisabled(
                        ThinkingConfigDisabled.builder().build()
                    )
                )
            }
        }

        toAnthropicTools(request.config.tools)
            .takeIf { it.isNotEmpty() }
            ?.let { paramsBuilder.tools(it) }

        postProcessParams(paramsBuilder, request)

        return paramsBuilder.build()
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
                    emitTextDeltaIfPresent(event)
//                    emitThinkingDeltaIfPresent(event)
                }
            }

            val message = accumulator.message()
            logger.debug { "Claude streaming complete: $message" }
            emit(message.toLlmResponse())

        } catch (e: AnthropicServiceException) {
            logger.error(e) { "Error processing Claude streaming response" }
            emit(mapToErrorResponse(e))
        }
    }

    protected open suspend fun FlowCollector<LlmResponse>.emitTextDeltaIfPresent(event: RawMessageStreamEvent) {
        if (!event.isContentBlockDelta()) return
        val delta = event.asContentBlockDelta().delta()
        if (!delta.isText()) return
        val text = delta.asText().text()
        emit(
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(Part(text = text))),
                partial = true
            )
        )
    }

//    private suspend fun FlowCollector<LlmResponse>.emitThinkingDeltaIfPresent(event: com.anthropic.models.messages.RawMessageStreamEvent) {
//        if (!event.isContentBlockDelta()) return
//        val delta = event.asContentBlockDelta().delta()
//        if (!delta.isThinking()) return
//        val thinking = delta.asThinking().thinking()
//        logger.trace { "Claude streaming text chunk: $thinking" }
//        emit(
//            LlmResponse(
//                content = Content(
//                    role = Role.MODEL,
//                    parts = listOf(Part(text = thinking, thought = true))
//                ),
//                partial = true
//            )
//        )
//    }

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

//            isThinking() -> Part(
//                text = asThinking().thinking(),
//                thought = true,
//                thoughtSignature = asThinking().signature().toByteArray(Charsets.UTF_8)
//            )

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
            val localFile = File(uri.removePrefix("file://"))
            if (localFile.isFile) {
                return Part(
                    inlineData = com.google.adk.kt.types.Blob(
                        mimeType = mimeType,
                        displayName = blob.displayName,
                        data = localFile.readBytes(),
                    ),
                ).toContentBlockParam()
            }
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
        else e.message
    )
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
