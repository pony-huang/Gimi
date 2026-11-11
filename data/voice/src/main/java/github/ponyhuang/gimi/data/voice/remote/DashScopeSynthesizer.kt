package github.ponyhuang.gimi.data.voice.remote

import javax.inject.Inject
import com.google.gson.JsonObject
import github.ponyhuang.gimi.core.audio.VoiceAudioFormat
import github.ponyhuang.gimi.core.network.VoiceWsKernel
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure

import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

/**
 * 阿里云百炼实时语音合成（Qwen-Audio-TTS）能力适配器。
 *
 * 交互流程：连接后发送 `run-task`（音色、格式、采样率），服务端回 `task-started`；
 * 客户端用 `continue-task` 发送待合成文本，用 `finish-task` 结束本轮；服务端在
 * `sentence-synthesis` 事件之后紧跟一个二进制音频帧，边合成边下发。
 *
 * 音频格式固定请求 24 kHz 单声道 PCM，与播放器的 AudioTrack 配置一致，服务端不需要重采样，
 * 首包到达即可播放。
 *
 * 失败语义：任务级失败走 [DashScopeTaskException]，不重试——合成按字符计费，
 * 重放同一段文本会产生重复费用。
 */
class DashScopeSynthesizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(DASHSCOPE_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    // 端点由厂商自有：官方默认域名写死，业务空间 ID 非空时改写专属域名
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val session = VoiceWsKernel(okHttpClient, buildRequest(request), handshakeFailure = ::voiceHandshakeFailure)
        val taskId = UUID.randomUUID().toString()
        var emitted = false
        try {
            session.sendControl(runTaskPayload(taskId, request))
            val frames = session.frames()
            // 任务开启确认必须限时：连接被心跳保活时，服务端"半死"不会以断连形式暴露，
            // 裸等 task-started 会把整段合成永久挂起。限时在真实调度器上执行（而非调用方的
            // 虚拟时间测试调度器），保证无论调用方处于何种上下文，超时都按真实时钟兜底。
            val started = withContext(Dispatchers.IO) {
                withTimeoutOrNull(TASK_START_TIMEOUT_MS.milliseconds) taskStart@{
                    for (frame in frames) {
                        when (frame) {
                            is VoiceWsKernel.WsFrame.Control -> when (frame.payload.dashScopeEventName()) {
                                DASHSCOPE_EVENT_TASK_STARTED -> {
                                    session.sendControl(continueTaskPayload(taskId, request.text))
                                    session.sendControl(finishTaskPayload(taskId))
                                    return@taskStart true
                                }
                                DASHSCOPE_EVENT_TASK_FAILED -> throw frame.payload.toDashScopeTaskException()
                                else -> Unit
                            }
                            is VoiceWsKernel.WsFrame.Failure -> throw frame.error
                            is VoiceWsKernel.WsFrame.Closed -> return@taskStart false
                            else -> Unit
                        }
                    }
                    false
                }
            }
            when (started) {
                null -> throw IOException("等待语音合成任务启动超时")
                false -> throw IOException("语音合成连接提前关闭")
                else -> Unit
            }
            frames@ for (frame in frames) {
                when (frame) {
                    is VoiceWsKernel.WsFrame.Control -> when (frame.payload.dashScopeEventName()) {
                        DASHSCOPE_EVENT_TASK_STARTED -> {
                            session.sendControl(continueTaskPayload(taskId, request.text))
                            session.sendControl(finishTaskPayload(taskId))
                        }
                        DASHSCOPE_EVENT_TASK_FINISHED -> break@frames
                        DASHSCOPE_EVENT_TASK_FAILED -> throw frame.payload.toDashScopeTaskException()
                        else -> Unit
                    }
                    is VoiceWsKernel.WsFrame.Audio -> {
                        emitted = true
                        emit(frame.bytes)
                    }
                    is VoiceWsKernel.WsFrame.Failure -> throw frame.error
                    is VoiceWsKernel.WsFrame.Closed -> throw IOException("语音合成连接提前关闭")
                    else -> Unit
                }
            }
            if (!emitted) throw IOException("语音合成未返回音频数据")
        } finally {
            // 合成失败或被取消时同样要断开连接，避免服务端继续为这次合成计费。
            session.close()
        }
    }

    private fun buildRequest(request: SynthesisRequest): Request = Request.Builder()
        // 接入地址由上游（仓库层）按业务空间解析好传入；这里只补业务空间请求头兜底
        .url(request.baseUrl)
        .header("Authorization", "Bearer ${request.apiKey}")
        .apply {
            request.workspaceId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                header(DASHSCOPE_WORKSPACE_HEADER, it)
            }
        }
        .build()

    private fun runTaskPayload(taskId: String, request: SynthesisRequest): JsonObject {
        val parameters = JsonObject().apply {
            addProperty("text_type", TEXT_TYPE_PLAIN)
            addProperty("voice", request.voiceId)
            addProperty("format", AUDIO_FORMAT_PCM)
            addProperty("sample_rate", VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ)
            addProperty("volume", DEFAULT_VOLUME)
            addProperty("rate", DEFAULT_RATE)
            addProperty("pitch", DEFAULT_PITCH)
        }
        val payload = JsonObject().apply {
            addProperty("task_group", DASHSCOPE_TASK_GROUP_AUDIO)
            addProperty("task", TASK_TTS)
            addProperty("function", FUNCTION_SPEECH_SYNTHESIZER)
            addProperty("model", request.modelId)
            add("parameters", parameters)
            add("input", JsonObject())
        }
        return dashScopeControlEvent(DASHSCOPE_ACTION_RUN_TASK, taskId, payload)
    }

    private fun continueTaskPayload(taskId: String, text: String): JsonObject =
        dashScopeControlEvent(
            action = DASHSCOPE_ACTION_CONTINUE_TASK,
            taskId = taskId,
            payload = JsonObject().apply {
                add("input", JsonObject().apply { addProperty("text", text) })
            },
        )

    private fun finishTaskPayload(taskId: String): JsonObject = dashScopeControlEvent(
        action = DASHSCOPE_ACTION_FINISH_TASK,
        taskId = taskId,
        // 百炼要求结束事件显式携带空 input；缺失时服务端会返回 InvalidParameter。
        payload = JsonObject().apply { add("input", JsonObject()) },
    )

    private companion object {
        const val TEXT_TYPE_PLAIN = "PlainText"
        const val AUDIO_FORMAT_PCM = "pcm"
        const val DEFAULT_VOLUME = 50
        const val DEFAULT_RATE = 1.0
        const val DEFAULT_PITCH = 1.0
        const val TASK_TTS = "tts"
        const val FUNCTION_SPEECH_SYNTHESIZER = "SpeechSynthesizer"
        /** 等待 task-started 的上限；连接被心跳保活时服务端半死无法靠断连感知，必须超时兜底。 */
        const val TASK_START_TIMEOUT_MS = 15_000L
    }
}
