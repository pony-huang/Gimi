package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.types.FunctionDeclaration
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
 * [github.ponyhuang.gimi.core.audio.Pcm16Resampler] 升采样；文本帧一律 base64。
 *
 * 工具（实时助手模式）：function 工具按 GA 的扁平结构下发（type/name/description/
 * parameters 顶层并列）；函数调用经 `response.output_item.added` 起始、
 * `response.function_call_arguments.delta/.done` 传参，结果由
 * [functionCallOutput] 写回后再以 [createResponse] 触发二轮推理。
 */
internal object OpenAiRealtimeProtocol {

    // —— 客户端事件类型 ——

    private const val CLIENT_SESSION_UPDATE = "session.update"
    private const val CLIENT_APPEND_AUDIO = "input_audio_buffer.append"
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

    /** server_vad 的静音判轮时长（毫秒）；GA 默认值，中文对话实测后再调。 */
    const val DEFAULT_SILENCE_DURATION_MS = 500L

    /** server_vad 的其余参数取 GA 默认（threshold 0.5 / prefix_padding 300ms）。 */
    private const val DEFAULT_PREFIX_PADDING_MS = 300L

    /**
     * 构建会话配置；连接握手成功后发送一次，`session.updated` 确认前不允许上行音频。
     * [tools] 非空时按 GA 扁平结构携带 function 工具声明（实时助手模式）。
     */
    fun sessionUpdate(
        instructions: String,
        voice: String,
        silenceDurationMs: Long = DEFAULT_SILENCE_DURATION_MS,
        tools: List<FunctionDeclaration> = emptyList(),
    ): JsonObject {
        val inputFormat = JsonObject().apply {
            addProperty("type", "audio/pcm")
            addProperty("rate", 24_000)
        }
        val transcription = JsonObject().apply {
            addProperty("model", INPUT_TRANSCRIPTION_MODEL)
        }
        val turnDetection = JsonObject().apply {
            addProperty("type", "server_vad")
            addProperty("threshold", 0.5)
            addProperty("prefix_padding_ms", DEFAULT_PREFIX_PADDING_MS)
            addProperty("silence_duration_ms", silenceDurationMs)
            addProperty("create_response", true)
            addProperty("interrupt_response", true)
        }
        val audioInput = JsonObject().apply {
            add("format", inputFormat)
            add("transcription", transcription)
            add("turn_detection", turnDetection)
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

    /** 构建 wss 连接请求：模型走查询参数，密钥走 Bearer 头；GA 不需要 Beta 头。 */
    fun connectRequest(baseUrl: String, model: String, apiKey: String): Request {
        // 目录允许的地址协议是 wss；OkHttp 只接受 http/https，wss/ws 映射为 https/http。
        val normalized = when {
            baseUrl.startsWith("wss://", ignoreCase = true) -> "https://" + baseUrl.substring(5)
            baseUrl.startsWith("ws://", ignoreCase = true) -> "http://" + baseUrl.substring(5)
            else -> baseUrl
        }
        val url = normalized.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("model", model)
            ?.build()
            ?: throw IllegalArgumentException("非法的 Realtime 接入地址：$baseUrl")
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()
    }

    /** 解析一帧服务端 JSON；未知事件向前兼容地忽略。 */
    fun parse(payload: JsonObject): OpenAiRealtimeEvent {
        val type = payload.get("type")?.asString ?: return OpenAiRealtimeEvent.Ignored
        return when (type) {
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
