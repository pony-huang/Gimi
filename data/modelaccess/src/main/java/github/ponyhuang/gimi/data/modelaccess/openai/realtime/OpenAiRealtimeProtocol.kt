package github.ponyhuang.gimi.data.modelaccess.openai.realtime

import com.google.adk.kt.types.FunctionDeclaration
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import github.ponyhuang.gimi.core.audio.Pcm16Resampler
import github.ponyhuang.gimi.data.modelaccess.shared.openAiFunctionToolArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * OpenAI Realtime（GA）的 WebSocket 事件协议。
 *
 * 只做两件事：把客户端事件手工构建为 Gson [JsonObject]、把服务端 JSON 帧解析成
 * [OpenAiRealtimeEvent]。会话配置的嵌套结构以 GA 参考为准：判轮与输入转写
 * 嵌在 `session.audio.input` 下，输出模态默认只出音频（转写事件随之附送）。
 *
 * 上行音频固定 24 kHz 单声道 PCM16（服务端不收 16k pcm），由调用方先经
 * [Pcm16Resampler] 升采样；文本帧一律 base64。
 *
 * 工具（实时助手模式）：function 工具按 GA 的扁平结构下发（type/name/description/
 * parameters 顶层并列）；函数调用经 `response.output_item.added` 起始、
 * `response.function_call_arguments.delta/.done` 传参，结果由
 * [functionCallOutput] 写回后再以 [createResponse] 触发二轮推理。
 */
internal object OpenAiRealtimeProtocol {

    /** 角色切换只更新指令，保留音色、音频配置与服务端上下文。 */
    fun instructionsUpdate(instructions: String): JsonObject = JsonObject().apply {
        addProperty("type", "session.update")
        add("session", JsonObject().apply {
            addProperty("type", "realtime")
            addProperty("instructions", instructions)
        })
    }


    /**
     * 构建消息类型的 [CLIENT_ITEM_CREATE] 事件。
     * 用户消息文本使用 input_text、图片使用 input_image；助手消息使用 output_text；系统消息使用 input_text。
     */
    fun createMessageItem(content: com.google.adk.kt.types.Content, itemId: String? = null): JsonObject {
        val roleStr = when (content.role) {
            com.google.adk.kt.types.Role.USER -> "user"
            com.google.adk.kt.types.Role.MODEL -> "assistant"
            com.google.adk.kt.types.Role.SYSTEM -> "system"
            else -> throw IllegalArgumentException("Unsupported role for realtime conversation item: ${content.role}")
        }
        val parts = JsonArray()
        content.parts.forEach { part ->
            if (part.thought == true) return@forEach
            if (part.text != null) {
                parts.add(JsonObject().apply {
                    addProperty("type", if (roleStr == "assistant") "output_text" else "input_text")
                    addProperty("text", part.text)
                })
            } else if (part.inlineData != null && roleStr == "user") {
                val blob = requireNotNull(part.inlineData)
                require(blob.mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
                    "Unsupported image mimeType: ${blob.mimeType}"
                }
                parts.add(JsonObject().apply {
                    addProperty("type", "input_image")
                    addProperty("image_url", "data:${blob.mimeType};base64,${requireNotNull(blob.data).toByteString().base64()}")
                })
            }
        }
        require(parts.size() > 0) { "Message item content parts must not be empty" }
        return JsonObject().apply {
            addProperty("type", CLIENT_ITEM_CREATE)
            add("item", JsonObject().apply {
                if (itemId != null) {
                    addProperty("id", itemId)
                }
                addProperty("type", "message")
                addProperty("role", roleStr)
                add("content", parts)
            })
        }
    }

    /** 一次手动消息的文字和图片必须属于同一个用户事件。 */
    fun userMessage(content: com.google.adk.kt.types.Content, itemId: String): JsonObject {
        require(content.role == com.google.adk.kt.types.Role.USER)
        return createMessageItem(content, itemId)
    }

    /**
     * 将会话历史转为 [CLIENT_ITEM_CREATE] 客户端事件列表，用于在新全双工会话初始化时注入历史上下文。
     * 过滤思考过程、不支持的非图片附件及空轮次。
     */
    fun historyMessages(history: List<com.google.adk.kt.types.Content>): List<JsonObject> = history.mapNotNull { content ->
        val filteredParts = content.parts.filter { part ->
            part.thought != true && (
                part.text != null ||
                (content.role == com.google.adk.kt.types.Role.USER && part.inlineData?.mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
            )
        }
        if (filteredParts.isEmpty()) null
        else runCatching { createMessageItem(content.copy(parts = filteredParts)) }.getOrNull()
    }

    // —— 客户端事件类型 ——

    private const val CLIENT_SESSION_UPDATE = "session.update"
    private const val CLIENT_APPEND_AUDIO = "input_audio_buffer.append"
    private const val CLIENT_COMMIT_AUDIO = "input_audio_buffer.commit"
    private const val CLIENT_TRUNCATE = "conversation.item.truncate"
    private const val CLIENT_CANCEL_RESPONSE = "response.cancel"
    private const val CLIENT_ITEM_CREATE = "conversation.item.create"
    private const val CLIENT_CREATE_RESPONSE = "response.create"

    // —— 服务端事件类型 ——

    private const val SERVER_SESSION_UPDATED = "session.updated"
    private const val SERVER_SPEECH_STARTED = "input_audio_buffer.speech_started"
    private const val SERVER_BUFFER_COMMITTED = "input_audio_buffer.committed"
    private const val SERVER_INPUT_TRANSCRIPT = "conversation.item.input_audio_transcription.completed"
    private const val SERVER_ITEM_ADDED = "response.output_item.added"
    private const val SERVER_AUDIO_DELTA = "response.output_audio.delta"
    private const val SERVER_TRANSCRIPT_DELTA = "response.output_audio_transcript.delta"
    private const val SERVER_FUNCTION_CALL_ARGS_DELTA = "response.function_call_arguments.delta"
    private const val SERVER_FUNCTION_CALL_ARGS_DONE = "response.function_call_arguments.done"
    private const val SERVER_RESPONSE_DONE = "response.done"
    private const val SERVER_ERROR = "error"

    /** 会话输入转写模型：用户侧字幕的服务端来源；不指定语言，交给服务端自动检测。 */
    private const val INPUT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe"

    /**
     * 构建会话配置；连接握手成功后发送一次，`session.updated` 确认前不允许上行音频。
     * 判轮完全由客户端本地 VAD 负责，因此关闭服务端自动判轮；[tools] 非空时按 GA
     * 扁平结构携带 function 工具声明（实时助手模式）。
     */
    fun sessionUpdate(
        instructions: String,
        voice: String,
        tools: List<FunctionDeclaration> = emptyList(),
    ): JsonObject {
        val inputFormat = JsonObject().apply {
            addProperty("type", "audio/pcm")
            addProperty("rate", 24_000)
        }
        val transcription = JsonObject().apply {
            addProperty("model", INPUT_TRANSCRIPTION_MODEL)
        }
        val audioInput = JsonObject().apply {
            add("format", inputFormat)
            add("transcription", transcription)
            add("turn_detection", JsonNull.INSTANCE)
        }
        val audioOutput = JsonObject().apply {
            add("format", JsonObject().apply {
                addProperty("type", "audio/pcm")
                addProperty("rate", 24_000)
            })
        }
        val modalities = JsonArray().apply { add("audio") }
        val session = JsonObject().apply {
            addProperty("type", "realtime")
            addProperty("instructions", instructions)
            addProperty("voice", voice)
            add("output_modalities", modalities)
            add("audio", JsonObject().apply {
                add("input", audioInput)
                add("output", audioOutput)
            })
            if (tools.isNotEmpty()) add("tools", openAiFunctionToolArray(tools))
        }
        return JsonObject().apply {
            addProperty("type", CLIENT_SESSION_UPDATE)
            add("session", session)
        }
    }

    /** 构建音频上行帧；输入为已重采样的 24 kHz PCM16 原始字节。 */
    fun appendAudio(pcm24k: ByteArray): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_APPEND_AUDIO)
        addProperty("audio", pcm24k.toByteString().base64())
    }

    /**
     * 构建打断截断：告知服务端客户端实际听到多少毫秒，未播放音频与其转写
     * 从服务端上下文中移除。[audioEndMs] 超过实际时长会被服务端拒绝。
     */
    fun truncate(assistantItemId: String, audioEndMs: Long): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_TRUNCATE)
        addProperty("item_id", assistantItemId)
        addProperty("content_index", 0)
        addProperty("audio_end_ms", audioEndMs)
    }

    /** 取消进行中的响应；无活动响应时服务端会回错误事件，会话不受影响。 */
    fun cancelResponse(): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_CANCEL_RESPONSE)
    }

    /**
     * 写回一次工具执行结果；[outputJson] 为 JSON 字符串，回执项以 call_id 关联
     * 服务端上下文中的 function_call 项。
     */
    fun functionCallOutput(callId: String, outputJson: String): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_ITEM_CREATE)
        add("item", JsonObject().apply {
            addProperty("type", "function_call_output")
            addProperty("call_id", callId)
            addProperty("output", outputJson)
        })
    }

    /** 触发一次新响应；写回工具结果后由调用方显式发送（服务端不自动续轮）。 */
    fun createResponse(): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_CREATE_RESPONSE)
    }

    /** 本地 VAD 判轮结束：先提交音频，再显式请求模型响应。 */
    fun commitAudioTurn(): List<JsonObject> = listOf(
        JsonObject().apply { addProperty("type", CLIENT_COMMIT_AUDIO) },
        createResponse(),
    )

    /** 构建 wss 连接请求：模型/通话 ID/意图走查询参数，密钥走 Bearer 头；GA 不需要 Beta 头。 */
    fun connectRequest(
        baseUrl: String,
        model: String,
        apiKey: String,
        callId: String? = null,
        intent: String? = null,
    ): Request {
        // 目录允许的地址协议是 wss；OkHttp 只接受 http/https，wss/ws 映射为 https/http。
        val normalized = when {
            baseUrl.startsWith("wss://", ignoreCase = true) -> "https://" + baseUrl.substring(5)
            baseUrl.startsWith("ws://", ignoreCase = true) -> "http://" + baseUrl.substring(5)
            else -> baseUrl
        }
        val builder = normalized.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("非法的 Realtime 接入地址：$baseUrl")
        if (model.isNotBlank()) {
            builder.addQueryParameter("model", model)
        }
        if (!callId.isNullOrBlank()) {
            builder.addQueryParameter("call_id", callId)
        }
        if (!intent.isNullOrBlank()) {
            builder.addQueryParameter("intent", intent)
        }
        return Request.Builder()
            .url(builder.build())
            .header("Authorization", "Bearer $apiKey")
            .build()
    }

    /** 解析一帧服务端 JSON；未知事件向前兼容地忽略。 */
    fun parse(payload: JsonObject): OpenAiRealtimeEvent {
        val type = payload.get("type")?.asString ?: return OpenAiRealtimeEvent.Ignored
        return when (type) {
            "response.created" -> OpenAiRealtimeEvent.ResponseStarted
            SERVER_SESSION_UPDATED -> OpenAiRealtimeEvent.SessionUpdated
            SERVER_SPEECH_STARTED -> OpenAiRealtimeEvent.SpeechStarted
            SERVER_BUFFER_COMMITTED -> OpenAiRealtimeEvent.TurnCommitted(payload.get("item_id")?.asString)
            SERVER_INPUT_TRANSCRIPT -> OpenAiRealtimeEvent.UserTranscript(payload.get("transcript")?.asString.orEmpty())
            SERVER_AUDIO_DELTA -> OpenAiRealtimeEvent.AudioDelta(
                payload.get("delta")?.asString.orEmpty().decodeBase64()?.toByteArray() ?: ByteArray(0),
            )
            SERVER_TRANSCRIPT_DELTA -> OpenAiRealtimeEvent.AssistantTranscriptDelta(
                payload.get("delta")?.asString.orEmpty(),
            )
            SERVER_ITEM_ADDED -> parseItemAdded(payload.getAsJsonObject("item"))
            SERVER_FUNCTION_CALL_ARGS_DELTA -> OpenAiRealtimeEvent.ToolCallArgumentsDelta(
                callId = payload.get("call_id")?.asString.orEmpty(),
                delta = payload.get("delta")?.asString.orEmpty(),
            )
            SERVER_FUNCTION_CALL_ARGS_DONE -> OpenAiRealtimeEvent.ToolCallArgumentsDone(
                callId = payload.get("call_id")?.asString.orEmpty(),
                name = payload.get("name")?.asString,
                arguments = payload.get("arguments")?.asString,
            )
            SERVER_RESPONSE_DONE -> OpenAiRealtimeEvent.ResponseDone(
                payload.getAsJsonObject("response")?.get("status")?.asString,
            )
            SERVER_ERROR -> {
                val error = payload.getAsJsonObject("error")
                OpenAiRealtimeEvent.ProtocolError(
                    message = error?.get("message")?.asString ?: "未知服务端错误",
                    code = error?.get("code")?.asString,
                )
            }
            else -> OpenAiRealtimeEvent.Ignored
        }
    }

    private fun parseItemAdded(item: JsonObject?): OpenAiRealtimeEvent {
        if (item == null) return OpenAiRealtimeEvent.Ignored
        return when (item.get("type")?.asString) {
            "function_call" -> OpenAiRealtimeEvent.ToolCallStarted(
                callId = item.get("call_id")?.asString.orEmpty(),
                name = item.get("name")?.asString.orEmpty(),
            )
            "message" -> if (item.get("role")?.asString == "assistant") {
                OpenAiRealtimeEvent.AssistantItemStarted(item.get("id")?.asString.orEmpty())
            } else {
                OpenAiRealtimeEvent.Ignored
            }
            else -> OpenAiRealtimeEvent.Ignored
        }
    }
}

/** 服务端事件的内核映射；模型层据此驱动 [com.google.adk.kt.models.LlmResponse] 输出。 */
internal sealed interface OpenAiRealtimeEvent {
    /** 新响应开始，解除手动打断的旧增量抑制。 */
    data object ResponseStarted : OpenAiRealtimeEvent

    /** 会话配置已生效；此后才允许上行音频。 */
    data object SessionUpdated : OpenAiRealtimeEvent

    /** 服务端检测到用户开始说话；播放中由模型侧触发协议截断。 */
    data object SpeechStarted : OpenAiRealtimeEvent

    /** 用户输入已提交成轮；编排层据此推进「响应中」。 */
    data class TurnCommitted(val itemId: String?) : OpenAiRealtimeEvent

    /** 用户语音的服务端转写（整句），供用户侧字幕。 */
    data class UserTranscript(val transcript: String) : OpenAiRealtimeEvent

    /** 助手回复音频块（已按 base64 解码，24 kHz PCM16）。 */
    class AudioDelta(val pcm24k: ByteArray) : OpenAiRealtimeEvent

    /** 助手回复转写增量，供助手侧字幕。 */
    data class AssistantTranscriptDelta(val text: String) : OpenAiRealtimeEvent

    /** 助手消息输出项开始；记下 item id 供打断截断使用。 */
    data class AssistantItemStarted(val itemId: String) : OpenAiRealtimeEvent

    /** 工具调用输出项开始（function_call item）；完整参数随后经参数增量/完成事件到达。 */
    data class ToolCallStarted(val callId: String, val name: String) : OpenAiRealtimeEvent

    /** 工具调用参数增量；按 call_id 累积，`arguments.done` 到达前不作执行依据。 */
    data class ToolCallArgumentsDelta(val callId: String, val delta: String) : OpenAiRealtimeEvent

    /** 工具调用参数接收完成；arguments 为完整参数 JSON，缺失时以累积增量为准。 */
    data class ToolCallArgumentsDone(
        val callId: String,
        val name: String?,
        val arguments: String?,
    ) : OpenAiRealtimeEvent

    /** 一条响应结束；status 为 completed / cancelled / incomplete / failed 等。 */
    data class ResponseDone(val status: String?) : OpenAiRealtimeEvent

    /** 协议级错误。 */
    data class ProtocolError(val message: String, val code: String?) : OpenAiRealtimeEvent

    /** 未知或与本链路无关的事件，向前兼容地忽略。 */
    data object Ignored : OpenAiRealtimeEvent
}
