package github.ponyhuang.asssistantai.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.JsonValue.Companion.from
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlock
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ContentBlockParam.Companion.ofImage
import com.anthropic.models.messages.ContentBlockParam.Companion.ofText
import com.anthropic.models.messages.ContentBlockParam.Companion.ofThinking
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolResult
import com.anthropic.models.messages.ContentBlockParam.Companion.ofToolUse
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.ThinkingConfigParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.ToolUseBlockParam
import com.anthropic.models.messages.Usage
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
import java.util.stream.Collectors

/**
 * Claude (Anthropic) Model — ADK [Model] bridge to the Anthropic Java SDK.

 * @author pony
 * @date 2026/6/28
 */
open class Claude(
    override val name: String,
    private val client: AnthropicClient,
) : Model {

    companion object {
        private val JSON_MAPPER = jsonMapper()
        private val logger = LoggerFactory.getLogger(Claude::class)
    }

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

    private fun buildMessageCreateParams(request: LlmRequest): MessageCreateParams {
        logger.debug { "LLmRequest: $request" }
        val modelName = request.model?.name ?: name
        val systemInstruction = request.config.systemInstruction?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("\n")
            .orEmpty()

        val messages = request.contents.stream()
            .map { contentToAnthropicMessageParam(it) }
            .collect(Collectors.toList())

        val paramsBuilder = MessageCreateParams.builder()
            .model(modelName)
            .system(systemInstruction)
            .messages(messages)
            .maxTokens(request.config.maxOutputTokens?.toLong() ?: 8196)

        request.config.thinkingConfig?.let {
            if (it.includeThoughts == false) {
                paramsBuilder.thinking(
                    ThinkingConfigParam.ofDisabled(
                        ThinkingConfigDisabled.builder().build()
                    )
                )
            }
        }

        request.config.tools
            ?.flatMap { it.functionDeclarations.orEmpty() }
            ?.map { ToolUnion.ofTool(it.toAnthropicTool()) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { paramsBuilder.tools(it) }

        return paramsBuilder.build()
    }

    private suspend fun FlowCollector<LlmResponse>.processStreamingResponse(params: MessageCreateParams) {
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

        } catch (e: Exception) {
            logger.error(e) {
                "Error processing streaming response, params: ${
                    JSON_MAPPER.writeValueAsString(
                        params
                    )
                }"
            }
            emit(mapToErrorResponse(e))
        }
    }

    private suspend fun FlowCollector<LlmResponse>.emitTextDeltaIfPresent(event: com.anthropic.models.messages.RawMessageStreamEvent) {
        if (!event.isContentBlockDelta()) return
        val delta = event.asContentBlockDelta().delta()
        if (!delta.isText()) return
        val text = delta.asText().text()
        logger.trace { "Claude streaming text chunk: $text" }
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

    private fun Message.toLlmResponse(): LlmResponse = LlmResponse(
        content = Content(
            role = Role.MODEL,
            parts = content().asSequence()
                .mapNotNull { it.toPart() }
                .toList()
        ),
        usageMetadata = usage().toUsageMetadata()
    )

    private fun Usage.toUsageMetadata() = UsageMetadata(
        promptTokenCount = inputTokens().toInt(),
        candidatesTokenCount = outputTokens().toInt(),
        totalTokenCount = inputTokens().toInt() + outputTokens().toInt()
    )

    private fun ContentBlock.toPart(): Part? {
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

    private fun contentToAnthropicMessageParam(content: Content) = MessageParam.builder()
        .role(toClaudeRole(content.role.toString()))
        .contentOfBlockParams(
            content.parts.stream()
                .map { it.toContentBlockParam() }
                .collect(Collectors.toList())
        )
        .build()

    private fun Part.toContentBlockParam(): ContentBlockParam {
        // Thinking blocks must be echoed back with their signature intact;
        // sending them as plain text triggers a 400 error from the API.
        if (thought == true) {
            return ofThinking(
                ThinkingBlockParam.builder()
                    .thinking(text ?: "")
                    .signature(thoughtSignature?.toString(Charsets.UTF_8) ?: "")
                    .build()
            )
        }
        text?.let { return ofText(TextBlockParam.builder().text(it).build()) }
        inlineData?.let { image ->
            val mimeType =
                image.mimeType ?: throw UnsupportedOperationException("Image MIME type is missing")
            val data = image.data ?: throw UnsupportedOperationException("Image data is missing")
            if (!mimeType.startsWith("image/")) {
                throw UnsupportedOperationException("Only image inline data is supported: $mimeType")
            }
            return ofImage(
                ImageBlockParam.builder()
                    .source(
                        Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.of(mimeType))
                            .data(Base64.getEncoder().encodeToString(data))
                            .build()
                    )
                    .build()
            )
        }
        functionCall?.let { fc ->
            return ofToolUse(
                ToolUseBlockParam.builder()
                    .id(fc.id.toString())
                    .name(fc.name)
                    .type(from("tool_use"))
                    .input(from(fc.partialArgs))
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
                    .isError(false)
                    .build()
            )
        }
        throw UnsupportedOperationException("Not supported yet. $this")
    }

    private fun toClaudeRole(role: String) = when (role) {
        "model", "assistant" -> MessageParam.Role.ASSISTANT
        else -> MessageParam.Role.USER
    }

    private fun FunctionDeclaration.toAnthropicTool(): com.anthropic.models.messages.Tool {
        // ADK represents a parameterless function with `parameters == null`.
        // Anthropic still requires `input_schema` to be a JSON object, so its
        // `properties` member must be an empty object rather than JSON null.
        val properties = com.anthropic.models.messages.Tool.InputSchema.Properties.builder()
            .additionalProperties(
                parameters?.properties.orEmpty().mapValues { (_, schema) ->
                    from(schema.toClaudeParameters())
                }
            )
            .build()
        val inputSchema = com.anthropic.models.messages.Tool.InputSchema.builder()
            .properties(properties)
            .required(parameters?.required.orEmpty())
            .build()

        return com.anthropic.models.messages.Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(inputSchema)
            .build()
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

    private fun mapToErrorResponse(e: Exception) = LlmResponse(
        finishReason = if (e is AnthropicServiceException) FinishReason.SAFETY
        else FinishReason.FINISH_REASON_UNSPECIFIED,
        errorMessage = if (e is AnthropicServiceException) "${e.statusCode()}: ${e.message}"
        else e.message
    )
}
