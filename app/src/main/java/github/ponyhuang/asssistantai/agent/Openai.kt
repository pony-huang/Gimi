package github.ponyhuang.asssistantai.agent

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
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.core.jsonMapper
import com.openai.errors.BadRequestException
import com.openai.errors.OpenAIException
import com.openai.helpers.ChatCompletionAccumulator.Companion.create
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.completions.CompletionUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.Base64


/**
 * OpenAI Model — ADK [Model] bridge to the OpenAI Java SDK.
 *
 * @author pony
 * @date 2026/6/21
 */
open class Openai(
    override val name: String,
    private val client: OpenAIClient,
) : Model {

    companion object {
        private val JSON_MAPPER = jsonMapper()
        private val logger = LoggerFactory.getLogger(Openai::class)
        private const val DEFAULT_MAX_COMPLETION_TOKENS = 2048L
    }

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val params = buildCreateParams(request)

        if (stream) {
            logger.debug { "Sending streaming request to OpenAI model ${params.model()}" }
            processStreamingResponse(params)
        } else {
            logger.debug { "Sending request to OpenAI model ${params.model()}" }
            val completion = try {
                client.chat().completions().create(params)
            } catch (e: OpenAIException) {
                emit(mapToErrorResponse(e))
                return@flow
            }
            logger.debug { "OpenAI response received" }
            emit(completion.toLlmResponse())
        }
    }

    private fun buildCreateParams(request: LlmRequest): ChatCompletionCreateParams {
        val modelName = request.model?.name ?: name

        val messages = mutableListOf<ChatCompletionMessageParam>().apply {
            request.config.systemInstruction
                ?.parts?.takeIf { it.isNotEmpty() }
                ?.toSystemMessage()
                ?.let(::add)
            request.contents.forEach { content ->
                when (content.role) {
                    Role.USER -> addAll(content.toUserMessages())
                    Role.MODEL -> addAll(content.toModelMessages())
                    else -> Unit
                }
            }
        }

        val tools = request.config.tools
            ?.flatMap { it.functionDeclarations.orEmpty() }
            ?.map { it.toChatCompletionTool() }
            .orEmpty()

        val builder = ChatCompletionCreateParams.builder()
            .model(modelName)
            .maxCompletionTokens(
                request.config.maxOutputTokens?.toLong() ?: DEFAULT_MAX_COMPLETION_TOKENS
            )
            .messages(messages)
            .also { if (tools.isNotEmpty()) it.tools(tools) }

        request.config.thinkingConfig?.let {
            if (it.includeThoughts == false) {
                builder.reasoningEffort(ReasoningEffort.NONE)
            }
        }

        return builder.build()
    }


    /**
     * Processes an SSE stream from the OpenAI Chat Completions API.
     *
     * Text deltas are emitted immediately as `partial = true` responses
     * so the UI can render incrementally. Tool call deltas are buffered
     * by their chunk `index` — OpenAI emits `id` and `function.name` on
     * the first chunk for a tool, then streams the `arguments` JSON
     * across subsequent chunks. The accumulated tool calls are emitted
     * as a single non-partial response once the stream ends, mirroring
     * the non-streaming [ChatCompletion] shape.
     */
    private suspend fun FlowCollector<LlmResponse>.processStreamingResponse(params: ChatCompletionCreateParams) {
        val chatCompletionAccumulator = create()
        try {
            client.chat().completions().createStreaming(params).use { streamResponse ->
                // Java streams are lazy. The previous pipeline had no terminal operation,
                // so it never read a chunk or let the SDK observe the final chunk required
                // by ChatCompletionAccumulator.chatCompletion().
                val chunks = streamResponse.stream().iterator()
                while (chunks.hasNext()) {
                    val chunk = chunks.next()
                    chatCompletionAccumulator.accumulate(chunk)
                    for (choice in chunk.choices()) {
                        emitTextDelta(choice)
                    }
                }
            }
            val chatCompletion = chatCompletionAccumulator.chatCompletion()
            val llmResponse = chatCompletion.toLlmResponse()
            llmResponse.content?.parts?.any {
                it.functionCall != null || it.functionResponse != null
            }.let {
                emit(llmResponse)
            }
        } catch (e: OpenAIException) {
            logger.error(e) { "Error processing OpenAI streaming response" }
            emit(mapToErrorResponse(e))
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error processing OpenAI streaming response" }
            emit(mapToErrorResponse(e))
        }
    }

    /**
     * Emits one `partial = true` [LlmResponse] for the text delta in [choice] (drives the
     * typewriter UI) and returns the same delta string so the caller can fold it into the
     * stream-end accumulator without re-parsing the chunk.
     */
    private suspend fun FlowCollector<LlmResponse>.emitTextDelta(
        choice: ChatCompletionChunk.Choice,
    ) {
        val text = choice.delta().content().orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?: return

        emit(
            LlmResponse(
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = text)),
                ),
                partial = true,
            ),
        )
    }


    private fun ChatCompletion.toLlmResponse(): LlmResponse = LlmResponse(
        content = Content(
            role = Role.MODEL,
            parts = choices().flatMap { it.message().toParts() }
        ),
        usageMetadata = usage().orElse(null)?.toUsageMetadata()
    )

    private fun CompletionUsage.toUsageMetadata() = UsageMetadata(
        promptTokenCount = promptTokens().toInt(),
        candidatesTokenCount = completionTokens().toInt(),
        totalTokenCount = totalTokens().toInt()
    )

    private fun com.openai.models.chat.completions.ChatCompletionMessage.toParts(): List<Part> {
        val parts = mutableListOf<Part>()
        content().ifPresent { text -> parts += Part(text = text) }
        toolCalls().ifPresent { calls ->
            calls.forEach { tc ->
                if (tc.isFunction()) {
                    val ftc = tc.asFunction()
                    parts += Part(
                        functionCall = FunctionCall(
                            id = ftc.id(),
                            name = ftc.function().name(),
                            args = parseJsonArgs(ftc.function().arguments())
                        )
                    )
                }
            }
        }
        return parts
    }

    private fun List<Part>.toSystemMessage(): ChatCompletionMessageParam {
        val text = mapNotNull { it.text }.joinToString("\n")
        return ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(text).build()
        )
    }

    private fun Content.toUserMessages(): List<ChatCompletionMessageParam> {
        val result = mutableListOf<ChatCompletionMessageParam>()

        val contentParts = buildList {
            parts.mapNotNull { it.text }.joinToString("\n").takeIf(String::isNotBlank)
                ?.let { text ->
                    add(
                        ChatCompletionContentPart.ofText(
                            ChatCompletionContentPartText.builder().text(text).build()
                        )
                    )
                }
            parts.mapNotNull { it.inlineData }.forEach { image ->
                val mimeType = image.mimeType ?: return@forEach
                val data = image.data ?: return@forEach
                if (mimeType.startsWith("image/")) {
                    val dataUrl =
                        "data:$mimeType;base64," + Base64.getEncoder().encodeToString(data)
                    add(
                        ChatCompletionContentPart.ofImageUrl(
                            ChatCompletionContentPartImage.builder()
                                .imageUrl(
                                    ChatCompletionContentPartImage.ImageUrl.builder()
                                        .url(dataUrl)
                                        .build()
                                )
                                .build()
                        )
                    )
                }
            }
        }
        if (contentParts.isNotEmpty()) {
            result += ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .contentOfArrayOfContentParts(contentParts)
                    .build()
            )
        }

        result += parts.mapNotNull { it.functionResponse }.map { fr ->
            val response = fr.response
            val resultContent = response["result"]
                ?.let { JSON_MAPPER.writeValueAsString(it) }
                ?: JSON_MAPPER.writeValueAsString(response)
            ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                    .toolCallId(fr.id.toString())
                    .content(resultContent)
                    .build()
            )
        }
        return result
    }

    private fun Content.toModelMessages(): List<ChatCompletionMessageParam> {
        val result = mutableListOf<ChatCompletionMessageParam>()

        val text = parts
            .filter { it.thought == false }
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .joinToString("\n")
        if (text.isNotEmpty()) {
            result += ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder().content(text).build()
            )
        }

        val toolCalls = parts.mapNotNull { it.functionCall }.map { fc ->
            ChatCompletionMessageFunctionToolCall.builder()
                .id(fc.id ?: "")
                .function(
                    ChatCompletionMessageFunctionToolCall.Function.builder()
                        .name(fc.name)
                        .arguments(JSON_MAPPER.writeValueAsString(fc.args))
                        .build()
                )
                .build()
        }
        if (toolCalls.isNotEmpty()) {
            val builder = ChatCompletionAssistantMessageParam.builder()
            toolCalls.forEach(builder::addToolCall)
            result += ChatCompletionMessageParam.ofAssistant(builder.build())
        }
        return result
    }

    private fun FunctionDeclaration.toChatCompletionTool(): ChatCompletionTool {
        val parameters = parameters?.toOpenAiParameters() ?: emptyMap()
        val functionDef = FunctionDefinition.builder()
            .name(name)
            .description(description)
            .parameters(FunctionParameters.builder().additionalProperties(parameters).build())
            .build()
        return ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder().function(functionDef).build()
        )
    }

    private fun Schema.toOpenAiParameters(): Map<String, JsonValue> {
        val result = mutableMapOf<String, JsonValue>()
        type?.let { result["type"] = JsonValue.from(it.name.lowercase()) }
        description?.let { result["description"] = JsonValue.from(it) }
        properties?.let {
            result["properties"] =
                JsonValue.from(it.mapValues { (_, v) -> v.toOpenAiParameters() })
        }
        required?.let { result["required"] = JsonValue.from(it) }
        items?.let { result["items"] = JsonValue.from(it.toOpenAiParameters()) }
        enum?.let { result["enum"] = JsonValue.from(it) }
        return result
    }

    private fun parseJsonArgs(arguments: String): Map<String, Any?> = try {
        JSON_MAPPER.readValue(arguments, object : TypeReference<Map<String, Any?>>() {})
    } catch (e: Exception) {
        logger.warn(e) { "Failed to parse tool call arguments: $arguments" }
        emptyMap()
    }

    private fun mapToErrorResponse(e: Exception): LlmResponse = LlmResponse(
        finishReason = if (e is BadRequestException) FinishReason.SAFETY
        else FinishReason.FINISH_REASON_UNSPECIFIED,
        errorMessage = e.message
    )
}
