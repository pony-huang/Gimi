package github.ponyhuang.gimi.agent.model

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
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessage
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.completions.CompletionUsage
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.Base64
import java.io.File
import com.google.adk.kt.types.Tool as AdkTool


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
        private const val WEB_SEARCH_TOOL_ID: String = "web_search"
        private val JSON_MAPPER = jsonMapper()
        private val logger = LoggerFactory.getLogger(Openai::class)
        private val THOUGHT_DELTA_FIELDS = setOf(
            "reasoning_content",
            "reasoning",
            "thinking",
            "thought",
        )
    }

    /** Default OpenAI `max_completion_tokens` when the request omits one. Override for vendor caps. */
    protected open val defaultMaxCompletionTokens: Long = 2048L

    /** Resolve `max_completion_tokens` for [request]: prefer explicit config, fall back to [defaultMaxCompletionTokens]. */
    protected open fun resolveMaxCompletionTokens(request: LlmRequest): Long =
        request.config.maxOutputTokens?.toLong() ?: defaultMaxCompletionTokens

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

    /** Translate ADK [LlmRequest] into OpenAI [ChatCompletionCreateParams]. Override to add vendor-specific request fields. */
    protected open fun buildCreateParams(request: LlmRequest): ChatCompletionCreateParams {
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

        val tools = toOpenAiTools(request.config.tools)

        val builder = ChatCompletionCreateParams.builder()
            .model(modelName)
            .maxCompletionTokens(
                resolveMaxCompletionTokens(request)
            )
            .messages(messages)
            .also { if (tools.isNotEmpty()) it.tools(tools) }

        request.config.thinkingConfig?.let {
            if (it.includeThoughts == false) {
                // OpenAI-compatible APIs no longer accept "none"; LOW keeps lightweight
                // requests such as title generation within the supported enum values.
                builder.reasoningEffort(ReasoningEffort.LOW)
            }
        }

        postProcessParams(builder, request)

        return builder.build()
    }

    /**
     * Composition hook invoked last inside [buildCreateParams], just before `.build()`.
     * Override to attach vendor-specific params (e.g. `temperature`, `top_p`, custom headers) without reimplementing the builder.
     */
    protected open fun postProcessParams(
        builder: ChatCompletionCreateParams.Builder,
        request: LlmRequest,
    ) {
        // Default: no-op.
    }


    /**
     * Processes an SSE stream from the OpenAI Chat Completions API.
     *
     * Text deltas are emitted immediately as `partial = true` responses
     * so the UI can render incrementally. Tool call deltas are buffered
     * by their chunk `index` — OpenAI emits `id` and `function.name` on
     * the first chunk for a tool, then streams the `arguments` JSON
     * across subsequent chunks. A local accumulator is used instead of
     * `ChatCompletionAccumulator`, because compatible services may include usage data in chunks
     * that do not conform to the SDK's strict completion shape.
     */
    /** Drive OpenAI streaming, accumulate chunks, and emit them as [LlmResponse] parts. Override to swap streaming behavior. */
    protected open suspend fun FlowCollector<LlmResponse>.processStreamingResponse(params: ChatCompletionCreateParams) {
        val responseAccumulator = StreamingResponseAccumulator()
        try {
            client.chat().completions().createStreaming(params).use { streamResponse ->
                val chunks = streamResponse.stream().iterator()
                while (chunks.hasNext()) {
                    val chunk = chunks.next()
                    val choices = chunk.choices()
                    chunk.usage().orElse(null)?.let(responseAccumulator::setUsage)
                    for (choice in choices) {
                        responseAccumulator.accumulateToolCall(choice)
                        choice.textDeltaOrNull()?.let { text ->
                            responseAccumulator.appendText(text)
                            emitTextDelta(text)
                        }
                    }
                }
            }
            emit(responseAccumulator.toLlmResponse())
        } catch (e: OpenAIException) {
            logger.error(e) { "Error processing OpenAI streaming response" }
            emit(mapToErrorResponse(e))
        }
    }

    /**
     * Extracts a regular-text delta from [ChatCompletionChunk.Choice].
     *
     * The returned value is used to emit a `partial = true` [LlmResponse] for the typewriter UI.
     * OpenAI-compatible providers commonly put reasoning in an extension field such as
     * `reasoning_content`; those deltas are discarded instead of being rendered as text.
     */
    /** Extract a regular-text delta from a streaming [ChatCompletionChunk.Choice]. Override to surface vendor-specific reasoning or content fields. */
    protected open fun ChatCompletionChunk.Choice.textDeltaOrNull(): String? {
        val delta = delta()
        delta.content().orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val thoughtFields = delta._additionalProperties().keys.intersect(THOUGHT_DELTA_FIELDS)
        if (thoughtFields.isNotEmpty()) {
            logger.trace { "Ignoring OpenAI streaming thought delta from $thoughtFields" }
        }
        return null
    }

    protected open suspend fun FlowCollector<LlmResponse>.emitTextDelta(text: String) {
        emit(
            LlmResponse(
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = text, thought = false)),
                ),
                partial = true,
            ),
        )
    }

    private inner class StreamingResponseAccumulator {
        private val text = StringBuilder()
        private val toolCalls = linkedMapOf<Pair<Long, Long>, StreamedToolCall>()
        private var usage: CompletionUsage? = null

        fun appendText(delta: String) {
            text.append(delta)
        }

        fun setUsage(usage: CompletionUsage) {
            this.usage = usage
        }

        fun accumulateToolCall(choice: ChatCompletionChunk.Choice) {
            choice.delta().toolCalls().orElse(emptyList()).forEach { toolCall ->
                val accumulated = toolCalls.getOrPut(choice.index() to toolCall.index()) {
                    StreamedToolCall()
                }
                toolCall.id().ifPresent { accumulated.id = it }
                toolCall.function().ifPresent { function ->
                    function.name().ifPresent { accumulated.name = it }
                    function.arguments().ifPresent { accumulated.arguments.append(it) }
                }
            }
        }

        fun toLlmResponse(): LlmResponse {
            val parts = buildList {
                text.takeIf { it.isNotEmpty() }
                    ?.let { add(Part(text = it.toString(), thought = false)) }
                toolCalls.values.forEach { toolCall ->
                    val name = toolCall.name ?: return@forEach
                    add(
                        Part(
                            functionCall = FunctionCall(
                                id = toolCall.id,
                                name = name,
                                args = parseJsonArgs(
                                    toolCall.arguments.toString().ifEmpty { "{}" }),
                            )
                        )
                    )
                }
            }
            return LlmResponse(
                content = Content(role = Role.MODEL, parts = parts),
                usageMetadata = usage?.toUsageMetadata(),
            )
        }
    }

    private class StreamedToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )


    protected open fun ChatCompletion.toLlmResponse(): LlmResponse = LlmResponse(
        content = Content(
            role = Role.MODEL,
            parts = choices().flatMap { it.message().toParts() }
        ),
        usageMetadata = usage().orElse(null)?.toUsageMetadata()
    )

    protected open fun CompletionUsage.toUsageMetadata() = UsageMetadata(
        promptTokenCount = promptTokens().toInt(),
        candidatesTokenCount = completionTokens().toInt(),
        totalTokenCount = totalTokens().toInt()
    )

    protected open fun ChatCompletionMessage.toParts(): List<Part> {
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

    protected open fun List<Part>.toSystemMessage(): ChatCompletionMessageParam {
        val text = mapNotNull { it.text }.joinToString("\n")
        return ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(text).build()
        )
    }

    protected open fun Content.toUserMessages(): List<ChatCompletionMessageParam> {
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

            parts.mapNotNull { it.inlineData }.forEach { blob ->
                val mimeType = blob.mimeType ?: return@forEach
                val data = blob.data ?: return@forEach
                buildInlineContentPart(mimeType, data, blob.displayName.orEmpty())?.let { add(it) }
            }

            parts.mapNotNull { it.fileData }.forEach { fileData ->
                val mimeType = fileData.mimeType ?: return@forEach
                val fileUri = fileData.fileUri ?: return@forEach
                val displayName = fileData.displayName ?: return@forEach
                buildFilePart(mimeType, displayName, fileUri)?.let { add(it) }
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

    protected open fun buildFilePart(
        mimeType: String,
        displayName: String,
        fileUri: String
    ): ChatCompletionContentPart? {
        val localFile = File(fileUri.removePrefix("file://"))
        if (localFile.isFile) {
            return buildInlineContentPart(mimeType, localFile.readBytes(), displayName)
        }
        if (isSupportedImageMimeType(mimeType)) {
            return ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                    .imageUrl(
                        ChatCompletionContentPartImage.ImageUrl.builder()
                            .url(fileUri)
                            .build()
                    )
                    .build()
            )
        }
        return null;
    }

    protected open fun buildInlineContentPart(
        mimeType: String,
        data: ByteArray,
        displayName: String = "",
    ): ChatCompletionContentPart? {
        if (isSupportedImageMimeType(mimeType)) {
            val dataUrl = "data:$mimeType;base64," + Base64.getEncoder().encodeToString(data)
            return ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                    .imageUrl(
                        ChatCompletionContentPartImage.ImageUrl.builder()
                            .url(dataUrl)
                            .build()
                    )
                    .build()
            )
        }
        if (isSupportedAudioMimeType(mimeType)) {
            val dataBase64 = Base64.getEncoder().encodeToString(data)
            val format = when (mimeType.lowercase()) {
                "audio/wav", "audio/x-wav" ->
                    ChatCompletionContentPartInputAudio.InputAudio.Format.WAV
                "audio/mpeg", "audio/mp3" ->
                    ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
                else -> return null
            }
            return ChatCompletionContentPart.ofInputAudio(
                ChatCompletionContentPartInputAudio.builder()
                    .inputAudio(
                        ChatCompletionContentPartInputAudio.InputAudio.builder()
                            .data(dataBase64)
                            .format(format)
                            .build()
                    )
                    .build()
            )
        }
        if (AttachmentCategory.from(mimeType, displayName) == AttachmentCategory.DOCUMENT) {
            val fileData = "data:$mimeType;base64," + Base64.getEncoder().encodeToString(data)
            return ChatCompletionContentPart.ofFile(
                ChatCompletionContentPart.File.builder()
                    .file(
                        ChatCompletionContentPart.File.FileObject.builder()
                            .filename(displayName)
                            .fileData(fileData)
                            .build()
                    )
                    .build()
            )
        }
        return null
    }
    protected open fun Content.toModelMessages(): List<ChatCompletionMessageParam> {
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

    protected open fun FunctionDeclaration.toChatCompletionTool(): ChatCompletionTool {
        // ADK represents parameterless functions with `parameters == null`, while OpenAI
        // requires every function's parameters schema to have an object root.
        val parameters = parameters?.toOpenAiParameters()?.toMutableMap() ?: mutableMapOf()
        parameters["type"] = JsonValue.from("object")
        parameters.putIfAbsent("properties", JsonValue.from(emptyMap<String, JsonValue>()))
        val functionDef = FunctionDefinition.builder()
            .name(name)
            .description(description)
            .parameters(FunctionParameters.builder().additionalProperties(parameters).build())
            .build()
        return ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder().function(functionDef).build()
        )
    }

    protected open fun toOpenAiTools(tools: List<AdkTool>?): List<ChatCompletionTool> =
        tools.orEmpty()
            .flatMap { it.functionDeclarations.orEmpty() }
            .map { declaration ->
                when (declaration.name) {
                    WEB_SEARCH_TOOL_ID -> openAiWebSearchTool()
                    else -> declaration.toChatCompletionTool()
                }
            }

    private fun openAiWebSearchTool(): ChatCompletionTool =
        ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .type(JsonValue.from(WEB_SEARCH_TOOL_ID))
                .function(
                    FunctionDefinition.builder()
                        .name(WEB_SEARCH_TOOL_ID)
                        .putAdditionalProperty(
                            "type",
                            JsonValue.from(WEB_SEARCH_TOOL_ID),
                        )
                        .build(),
                )
                .build(),
        )

    protected open fun Schema.toOpenAiParameters(): Map<String, JsonValue> {
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

    protected open val defaultImageMimePrefix: String = "image/"

    protected open fun isSupportedImageMimeType(mimeType: String): Boolean =
        mimeType.startsWith(defaultImageMimePrefix)

    protected open fun isSupportedAudioMimeType(mimeType: String): Boolean =
        mimeType.startsWith("audio/")

    protected open fun mapToErrorResponse(e: Exception): LlmResponse = LlmResponse(
        finishReason = if (e is BadRequestException) FinishReason.SAFETY
        else FinishReason.FINISH_REASON_UNSPECIFIED,
        errorMessage = e.message
    )
}
