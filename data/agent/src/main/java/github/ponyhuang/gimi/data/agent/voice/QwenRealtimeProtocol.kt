package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.types.FunctionDeclaration
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * 阿里云百炼 Qwen-Audio Realtime 的 WebSocket 事件协议。
 *
 * 事件词汇与 OpenAI Realtime 同源但结构不同，差异点集中在：
 * - 上行原生接受 16 kHz PCM16（OpenAI 要 24k），下行固定 24 kHz；格式用
 *   `input_audio_format`/`output_audio_format` 的 `"pcm"` 字符串声明，采样率不随会话配置。
 * - 音色仅在**第一次** `session.update` 生效；重连即新会话，重新下发即可。
 * - 打断由服务端自动完成（`response.done` 返回 `status=cancelled`），客户端不发送
 *   截断/取消事件，只在 `speech_started` 后丢弃残余音频增量（静默窗口）。
 * - 请求头恒定附加 `x-dashscope-dataInspection: disable`（跟随官方示例的默认行为）。
 *
 * 工具（实时助手模式）：function 工具按官方文档的嵌套结构下发（声明对象包在
 * `function` 键下）；函数调用经 `response.output_item.added` 起始、
 * `response.function_call_arguments.delta/.done` 传参，结果由 [functionCallOutput]
 * 写回后再以 [createResponse] 触发二轮推理（官方文档步骤 3/4）。
 */
internal object QwenRealtimeProtocol {

    // —— 客户端事件类型 ——

    private const val CLIENT_SESSION_UPDATE = "session.update"
    private const val CLIENT_APPEND_AUDIO = "input_audio_buffer.append"
    private const val CLIENT_ITEM_CREATE = "conversation.item.create"
    private const val CLIENT_CREATE_RESPONSE = "response.create"

    // —— 服务端事件类型 ——

    private const val SERVER_SESSION_UPDATED = "session.updated"
    private const val SERVER_SPEECH_STARTED = "input_audio_buffer.speech_started"
    private const val SERVER_BUFFER_COMMITTED = "input_audio_buffer.committed"
    private const val SERVER_INPUT_TRANSCRIPT_DELTA = "conversation.item.input_audio_transcription.delta"
    private const val SERVER_INPUT_TRANSCRIPT_COMPLETED = "conversation.item.input_audio_transcription.completed"
    private const val SERVER_RESPONSE_CREATED = "response.created"
    private const val SERVER_ITEM_ADDED = "response.output_item.added"
    private const val SERVER_AUDIO_DELTA = "response.audio.delta"
    private const val SERVER_TRANSCRIPT_DELTA = "response.audio_transcript.delta"
    private const val SERVER_TRANSCRIPT_DONE = "response.audio_transcript.done"
    private const val SERVER_FUNCTION_CALL_ARGS_DELTA = "response.function_call_arguments.delta"
    private const val SERVER_FUNCTION_CALL_DONE = "response.function_call_arguments.done"
    private const val SERVER_RESPONSE_DONE = "response.done"
    private const val SERVER_ERROR = "error"

    /** server_vad 的静音判轮时长（毫秒）；官方推荐对话区间 400-800，取中值与 OpenAI 路径一致。 */
    const val DEFAULT_SILENCE_DURATION_MS = 500L

    /** server_vad 的灵敏度取官方默认 0.5。 */
    private const val DEFAULT_VAD_THRESHOLD = 0.5

    /** 关闭平台侧内容审查的请求头；跟随官方示例恒定下发。 */
    private const val DATA_INSPECTION_HEADER = "x-dashscope-dataInspection"

    /** 百炼业务空间请求头；业务空间 ID 非空时与专属域名一同下发（与官方 SDK 行为一致）。 */
    private const val WORKSPACE_HEADER = "X-DashScope-WorkSpace"

    /**
     * 构建会话配置；连接握手成功后发送一次，`session.updated` 确认前不允许上行音频。
     * 音色只在本事件（首连接的首次下发）中生效，重连后随新会话重新下发。
     * [tools] 非空时按官方嵌套结构携带 function 工具声明（实时助手模式）。
     */
    fun sessionUpdate(
        instructions: String,
        voice: String,
        silenceDurationMs: Long = DEFAULT_SILENCE_DURATION_MS,
        tools: List<FunctionDeclaration> = emptyList(),
    ): JsonObject {
        val turnDetection = JsonObject().apply {
            addProperty("type", "server_vad")
            addProperty("threshold", DEFAULT_VAD_THRESHOLD)
            addProperty("silence_duration_ms", silenceDurationMs)
        }
        val modalities = JsonArray().apply {
            add("audio")
            add("text")
        }
        val session = JsonObject().apply {
            add("modalities", modalities)
            addProperty("voice", voice)
            addProperty("instructions", instructions)
            addProperty("input_audio_format", "pcm")
            addProperty("output_audio_format", "pcm")
            add("turn_detection", turnDetection)
            if (tools.isNotEmpty()) add("tools", qwenFunctionToolArray(tools))
        }
        return JsonObject().apply {
            addProperty("type", CLIENT_SESSION_UPDATE)
            add("session", session)
        }
    }

    /** 构建音频上行帧；输入为 16 kHz PCM16 原始字节（服务端原生采样率，免重采样）。 */
    fun appendAudio(pcm16k: ByteArray): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_APPEND_AUDIO)
        addProperty("audio", pcm16k.toByteString().base64())
    }

    /**
     * 写回一次工具执行结果；[outputJson] 为 JSON 字符串，回执项以 call_id 关联
     * 服务端上下文中的 function_call 项（官方文档步骤 3）。
     */
    fun functionCallOutput(callId: String, outputJson: String): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_ITEM_CREATE)
        add("item", JsonObject().apply {
            addProperty("type", "function_call_output")
            addProperty("call_id", callId)
            addProperty("output", outputJson)
        })
    }

    /** 触发服务端基于已写回的工具结果继续生成（官方文档步骤 4 的二轮推理）。 */
    fun createResponse(): JsonObject = JsonObject().apply {
        addProperty("type", CLIENT_CREATE_RESPONSE)
    }

    /**
     * 构建 wss 连接请求：模型走查询参数，密钥走 Bearer 头，恒定附加内容审查开关头。
     *
     * @param baseUrl 接入地址（官方默认域名，或业务空间 ID 改写出的专属域名）。
     * @param workspaceId 百炼业务空间 ID；非空时额外携带业务空间请求头兜底，
     *   与官方 SDK「域名与请求头指名同一业务空间」的行为一致。
     */
    fun connectRequest(baseUrl: String, model: String, apiKey: String, workspaceId: String? = null): Request {
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
            .header(DATA_INSPECTION_HEADER, "disable")
            .apply {
                workspaceId?.trim()?.takeIf { it.isNotEmpty() }?.let { header(WORKSPACE_HEADER, it) }
            }
            .build()
    }

    /** 解析一帧服务端 JSON；未知事件向前兼容地忽略（smart_turn 专属事件在 server_vad 下不出现）。 */
    fun parse(payload: JsonObject): QwenRealtimeEvent {
        val type = payload.get("type")?.asString ?: return QwenRealtimeEvent.Ignored
        return when (type) {
            SERVER_SESSION_UPDATED -> QwenRealtimeEvent.SessionUpdated
            SERVER_SPEECH_STARTED -> QwenRealtimeEvent.SpeechStarted
            SERVER_BUFFER_COMMITTED -> QwenRealtimeEvent.TurnCommitted(payload.get("item_id")?.asString)
            SERVER_INPUT_TRANSCRIPT_DELTA -> QwenRealtimeEvent.UserTranscriptDelta(
                // text 为已确定的累计文本；stash 是未确定的暂存文本，不进字幕。
                payload.get("text")?.asString.orEmpty(),
            )
            SERVER_INPUT_TRANSCRIPT_COMPLETED -> QwenRealtimeEvent.UserTranscript(
                payload.get("transcript")?.asString.orEmpty(),
            )
            SERVER_RESPONSE_CREATED -> QwenRealtimeEvent.ResponseCreated
            SERVER_AUDIO_DELTA -> QwenRealtimeEvent.AudioDelta(
                payload.get("delta")?.asString.orEmpty().decodeBase64()?.toByteArray() ?: ByteArray(0),
            )
            SERVER_TRANSCRIPT_DELTA -> QwenRealtimeEvent.AssistantTranscriptDelta(
                payload.get("delta")?.asString.orEmpty(),
            )
            SERVER_TRANSCRIPT_DONE -> QwenRealtimeEvent.AssistantTranscriptDone(
                payload.get("transcript")?.asString.orEmpty(),
            )
            SERVER_ITEM_ADDED -> parseItemAdded(payload.getAsJsonObject("item"))
            SERVER_FUNCTION_CALL_ARGS_DELTA -> QwenRealtimeEvent.ToolCallArgumentsDelta(
                callId = payload.get("call_id")?.asString.orEmpty(),
                delta = payload.get("delta")?.asString.orEmpty(),
            )
            SERVER_FUNCTION_CALL_DONE -> QwenRealtimeEvent.ToolCallArgumentsDone(
                callId = payload.get("call_id")?.asString.orEmpty(),
                name = payload.get("name")?.asString,
                arguments = payload.get("arguments")?.asString,
            )
            SERVER_RESPONSE_DONE -> QwenRealtimeEvent.ResponseDone(
                payload.getAsJsonObject("response")?.get("status")?.asString,
            )
            SERVER_ERROR -> {
                val error = payload.getAsJsonObject("error")
                QwenRealtimeEvent.ProtocolError(
                    message = error?.get("message")?.asString ?: "未知服务端错误",
                    code = error?.get("code")?.asString,
                )
            }
            else -> QwenRealtimeEvent.Ignored
        }
    }

    private fun parseItemAdded(item: JsonObject?): QwenRealtimeEvent {
        if (item == null) return QwenRealtimeEvent.Ignored
        return when (item.get("type")?.asString) {
            // 完整参数随后经 response.function_call_arguments.delta/.done 到达，
            // 输出项事件只作为执行前的起始标记。
            "function_call" -> QwenRealtimeEvent.ToolCallStarted(
                callId = item.get("call_id")?.asString.orEmpty(),
                name = item.get("name")?.asString.orEmpty(),
            )
            else -> QwenRealtimeEvent.Ignored
        }
    }
}

/** 服务端事件的内核映射；模型层据此驱动 [com.google.adk.kt.models.LlmResponse] 输出。 */
internal sealed interface QwenRealtimeEvent {

    /** 会话配置已生效；此后才允许上行音频。 */
    data object SessionUpdated : QwenRealtimeEvent

    /** 服务端检测到用户开始说话；进入静默窗口，服务端自动打断进行中的响应。 */
    data object SpeechStarted : QwenRealtimeEvent

    /** 用户输入已提交成轮；编排层据此推进「响应中」。 */
    data class TurnCommitted(val itemId: String?) : QwenRealtimeEvent

    /** 用户语音转写的已确定文本增量（累计），供用户侧字幕替换渲染。 */
    data class UserTranscriptDelta(val text: String) : QwenRealtimeEvent

    /** 用户语音的服务端转写终稿（整句），供用户侧字幕。 */
    data class UserTranscript(val transcript: String) : QwenRealtimeEvent

    /** 一条响应开始；静默窗口在此解除，残余音频恢复下发。 */
    data object ResponseCreated : QwenRealtimeEvent

    /** 助手回复音频块（已按 base64 解码，24 kHz PCM16）。 */
    class AudioDelta(val pcm24k: ByteArray) : QwenRealtimeEvent

    /** 助手回复转写增量，供助手侧字幕。 */
    data class AssistantTranscriptDelta(val text: String) : QwenRealtimeEvent

    /** 助手回复转写终稿（完整文本）；已有增量时忽略，防字幕重复。 */
    data class AssistantTranscriptDone(val transcript: String) : QwenRealtimeEvent

    /** 工具调用输出项开始（function_call item）；完整参数随后经参数增量/完成事件到达。 */
    data class ToolCallStarted(val callId: String, val name: String) : QwenRealtimeEvent

    /** 工具调用参数增量；按 call_id 累积，`arguments.done` 到达前不作执行依据。 */
    data class ToolCallArgumentsDelta(val callId: String, val delta: String) : QwenRealtimeEvent

    /** 工具调用参数接收完成；arguments 为完整参数 JSON，缺失时以累积增量为准。 */
    data class ToolCallArgumentsDone(
        val callId: String,
        val name: String?,
        val arguments: String?,
    ) : QwenRealtimeEvent

    /** 一条响应结束；status 为 completed / cancelled / failed，cancelled 即被用户打断。 */
    data class ResponseDone(val status: String?) : QwenRealtimeEvent

    /** 协议级错误。 */
    data class ProtocolError(val message: String, val code: String?) : QwenRealtimeEvent

    /** 未知或与本链路无关的事件，向前兼容地忽略。 */
    data object Ignored : QwenRealtimeEvent
}
