package github.ponyhuang.gimi.data.modelaccess.qwen.speech

import javax.inject.Inject
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import github.ponyhuang.gimi.core.network.VoiceWsKernel
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.model.SpeechContextRole
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure

import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent

import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest

import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechRecognizer

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import okhttp3.OkHttpClient
import okhttp3.Request

/** 百炼语音服务标识；与模型目录中的服务 ID 一致。 */
internal const val DASHSCOPE_SERVICE_ID = "qwen"

/**
 * 百炼语音识别的协议内核。
 *
 * 交互流程：连接后发送 `run-task`（识别函数与音频参数），服务端回 `task-started`；
 * 客户端用二进制帧发送单声道 PCM 音频，服务端持续回 `result-generated`；
 * 音频发完后发送 `finish-task`，服务端回 `task-finished`。
 *
 * 音频参数固定 16 kHz 单声道 PCM，与应用录音链路一致，服务端不需要重采样。
 *
 * 失败语义：握手阶段的 401/403 走 [VoiceHandshakeException]，任务级失败走
 * [DashScopeTaskException]。两者都不重试——语音识别按音频时长计费，重放会产生重复费用。
 *
 * 内核只负责协议；一次性与流式两种能力由外层适配器表达。
 */
class DashScopeRecognitionProtocol(
    private val okHttpClient: OkHttpClient,
) {
    /**
     * 执行一次识别。
     *
     * @param streaming 是否产出中间结果；关闭时只产出最终文本。
     */
    fun recognize(request: RecognitionRequest, streaming: Boolean): Flow<RecognitionEvent> = flow {
        val session = VoiceWsKernel(okHttpClient, buildRequest(request), handshakeFailure = ::voiceHandshakeFailure)
        val taskId = UUID.randomUUID().toString()
        try {
            session.sendControl(runTaskPayload(taskId, request, streaming))
            val frames = session.frames()
            val sentences = LinkedHashMap<Int, String>()
            var finished = false
            // 任务开启确认必须限时：连接被心跳保活时，服务端"半死"不会以断连形式暴露，
            // 裸等 task-started 会把整段识别永久挂起。限时在真实调度器上执行（而非调用方的
            // 虚拟时间测试调度器），保证无论调用方处于何种上下文，超时都按真实时钟兜底。
            val started = withContext(Dispatchers.IO) {
                withTimeoutOrNull(TASK_START_TIMEOUT_MS) taskStart@{
                    for (frame in frames) {
                        when (frame) {
                            is VoiceWsKernel.WsFrame.Control -> when (frame.payload.dashScopeEventName()) {
                                DASHSCOPE_EVENT_TASK_STARTED -> {
                                    // 只有服务端确认任务开启后才能发送音频。
                                    sendAudio(session, request.pcm16)
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
                null -> throw IOException("等待语音识别任务启动超时")
                false -> throw IOException("语音识别连接提前关闭")
                else -> Unit
            }
            frames@ for (frame in frames) {
                when (frame) {
                    is VoiceWsKernel.WsFrame.Control -> when (frame.payload.dashScopeEventName()) {
                        DASHSCOPE_EVENT_RESULT_GENERATED -> {
                            val text = frame.payload.dashScopeSentenceText()
                            if (text.isNotBlank()) {
                                // 同一句可能先来中间结果再来最终结果，按句子序号覆盖即可。
                                sentences[frame.payload.dashScopeSentenceId()] = text
                                if (streaming && !frame.payload.dashScopeSentenceEnded()) {
                                    // 中间结果是整段音频的当前最优猜测：已定稿的句子加上当前这句的中间结果。
                                    emit(RecognitionEvent.Partial(sentences.values.joinToString(separator = "")))
                                }
                            }
                        }
                        DASHSCOPE_EVENT_TASK_FINISHED -> {
                            finished = true
                            if (streaming) {
                                // 整段音频识别完毕，只在此处给一次最终结果。
                                val text = sentences.values.filter { it.isNotBlank() }.joinToString(separator = "")
                                if (text.isBlank()) throw IOException("语音识别未返回文本")
                                emit(RecognitionEvent.Final(text))
                            }
                            break@frames
                        }
                        DASHSCOPE_EVENT_TASK_FAILED -> throw frame.payload.toDashScopeTaskException()
                    }
                    is VoiceWsKernel.WsFrame.Audio -> Unit
                    is VoiceWsKernel.WsFrame.Failure -> throw frame.error
                    is VoiceWsKernel.WsFrame.Closed ->
                        if (!finished) throw IOException("语音识别连接提前关闭")
                    is VoiceWsKernel.WsFrame.Opened -> Unit
                }
            }
            if (!streaming) {
                val text = sentences.values.filter { it.isNotBlank() }.joinToString(separator = "")
                if (text.isBlank()) throw IOException("语音识别未返回文本")
                emit(RecognitionEvent.Final(text))
            }
        } finally {
            // 无论成功、失败还是被取消都要关闭连接：服务端按连接时长计费，
            // 悬着的连接会继续计费，而重连重发音频同样会产生重复费用。
            session.close()
        }
    }

    private fun buildRequest(request: RecognitionRequest): Request = Request.Builder()
        // 接入地址由上游（仓库层）按业务空间解析好传入；这里只补业务空间请求头兜底
        .url(request.baseUrl)
        .header("Authorization", "Bearer ${request.apiKey}")
        .apply {
            request.workspaceId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                header(DASHSCOPE_WORKSPACE_HEADER, it)
            }
        }
        .build()

    private fun runTaskPayload(
        taskId: String,
        request: RecognitionRequest,
        streaming: Boolean,
    ): JsonObject {
        val parameters = JsonObject().apply {
            addProperty("format", "pcm")
            addProperty("sample_rate", request.sampleRateHz)
            addProperty("keep_dialect", false)
            // 手机麦克风是近讲场景；服务端默认按远场会议处理，会降低近场识别效果。
            addProperty("vad_model", VAD_MODEL_NEAR_MEETING)
            // 流式模型与 message 模型的参数集不同：流式模型本身逐句下发中间结果，
            // 既没有 intermediate_result_enabled 开关，也没有 disfluency_removal_enabled；
            // 多传未知参数会被服务端判为 InvalidParameter，整段音频白跑一趟。
            if (!isStreamingModel(request.modelId)) {
                addProperty("intermediate_result_enabled", streaming)
                addProperty("disfluency_removal_enabled", false)
            }
        }
        val input = JsonObject()
        if (request.context.isNotEmpty()) {
            input.add("context", contextArray(request))
        }
        val payload = JsonObject().apply {
            addProperty("task_group", DASHSCOPE_TASK_GROUP_AUDIO)
            addProperty("task", TASK_ASR)
            addProperty("function", FUNCTION_RECOGNITION)
            addProperty("model", request.modelId)
            add("parameters", parameters)
            add("input", input)
        }
        return dashScopeControlEvent(DASHSCOPE_ACTION_RUN_TASK, taskId, payload)
    }

    /** 对话上下文：用户侧是此前的识别结果或领域词表，助手侧是此前的回复。 */
    private fun contextArray(request: RecognitionRequest): JsonArray {
        val array = JsonArray()
        request.context.forEach { message ->
            val isUser = message.role == SpeechContextRole.User
            array.add(JsonObject().apply {
                addProperty("role", if (isUser) ROLE_USER else ROLE_ASSISTANT)
                add("content", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", if (isUser) TYPE_INPUT_TEXT else TYPE_TEXT)
                        addProperty("text", message.text)
                    })
                })
            })
        }
        return array
    }

    private fun finishTaskPayload(taskId: String): JsonObject = dashScopeControlEvent(
        action = DASHSCOPE_ACTION_FINISH_TASK,
        taskId = taskId,
        // 百炼要求结束事件显式携带空 input；缺失时服务端会返回 InvalidParameter。
        payload = JsonObject().apply { add("input", JsonObject()) },
    )

    private suspend fun sendAudio(session: VoiceWsKernel, pcm16: ByteArray) {
        var offset = 0
        while (offset < pcm16.size) {
            val end = minOf(offset + AUDIO_CHUNK_BYTES, pcm16.size)
            session.sendAudio(pcm16.copyOfRange(offset, end))
            offset = end
        }
    }

    private companion object {
        const val VAD_MODEL_NEAR_MEETING = "near_meeting_16k"
        const val TASK_ASR = "asr"
        const val FUNCTION_RECOGNITION = "recognition"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val TYPE_INPUT_TEXT = "input_text"
        const val TYPE_TEXT = "text"
        /** 约 1 秒的 16 kHz 单声道 PCM16。 */
        const val AUDIO_CHUNK_BYTES = 32 * 1024
        /** 流式识别模型的 ID 后缀；这类模型与 `*-message` 模型共用同一套 WebSocket 协议。 */
        const val STREAMING_MODEL_SUFFIX = "-streaming"
        /** 等待 task-started 的上限；连接被心跳保活时服务端半死无法靠断连感知，必须超时兜底。 */
        const val TASK_START_TIMEOUT_MS = 15_000L
    }

    /** 是否为流式识别模型。 */
    private fun isStreamingModel(modelId: String): Boolean =
        modelId.endsWith(STREAMING_MODEL_SUFFIX)
}

/**
 * 百炼的一次性语音识别能力。
 *
 * 只产出最终文本：服务端中间结果被关闭，识别完成后返回完整文本。
 */
class DashScopeOneShotRecognizer @Inject constructor(
    private val protocol: DashScopeRecognitionProtocol,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = setOf(DASHSCOPE_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.STT)

    // 端点由厂商自有：官方默认域名写死，业务空间 ID 非空时改写专属域名
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override suspend fun recognize(request: RecognitionRequest): String =
        protocol.recognize(request, streaming = false)
            .filterIsInstance<RecognitionEvent.Final>()
            .last()
            .text
}

/**
 * 百炼的流式语音识别能力。
 *
 * 服务端开启中间结果后，逐句产出中间结果与最终结果。
 */
class DashScopeStreamingRecognizer @Inject constructor(
    private val protocol: DashScopeRecognitionProtocol,
) : StreamingSpeechRecognizer {
    override val serviceIds: Set<String> = setOf(DASHSCOPE_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.STT)

    // 端点由厂商自有：官方默认域名写死，业务空间 ID 非空时改写专属域名
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override fun recognize(request: RecognitionRequest): Flow<RecognitionEvent> =
        protocol.recognize(request, streaming = true)
}
