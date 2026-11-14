@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess.qwen.realtime

import android.util.Log
import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Transcription
import github.ponyhuang.gimi.core.network.VoiceWsKernel
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import github.ponyhuang.gimi.data.modelaccess.VoiceAudioMarkers
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeFunctionCallSink
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeToolCallBuffer
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeResponseDisposition
import github.ponyhuang.gimi.data.modelaccess.shared.realtimeResponseDisposition
import github.ponyhuang.gimi.data.modelaccess.shared.parseFunctionArguments

/**
 * 百炼 Qwen-Audio Realtime 原生语音模型。
 *
 * 与 [OpenAiRealtimeModel] 同为常驻全双工会话，协议差异见 [QwenRealtimeProtocol]：
 * 上行原生 16 kHz PCM16 直喂（不经重采样）、下行 24 kHz；打断由服务端自动完成，
 * 模型侧只维护**静默窗口**——`speech_started` 后丢弃残余音频增量直到下一个
 * `response.created`，不发送任何截断/取消事件。
 *
 * 契约要点（与 OpenAI 路径一致的部分）：
 * - 文本通过 [LlmResponse.inputTranscription] 承载用户侧转写（增量替换 + 终稿）、
 *   [LlmResponse.outputTranscription] 承载助手侧转写增量；
 * - 判轮提交以 [VoiceAudioMarkers.TURN_COMMITTED] 标记事件下发；
 * - 工具：function 工具声明随 `session.update` 下发；服务端的 function_call 经
 *   参数增量/完成事件定稿后，以 [LlmResponse] 的 functionCall part 上抛，由门面
 *   执行并经 [RealtimeFunctionCallSink] 写回结果、触发二轮推理；
 * - 音频上行在 `session.updated` 确认前进入小缓冲（满则丢最旧），确认后一次冲出；
 *   重连即新会话（上下文不重放、音色重新下发），由编排层控制退避。
 */
internal class QwenRealtimeModel(
    private val endpoint: String,
    private val model: String,
    private val apiKey: String,
    /** 会话音色；会话首个 session.update 下发，测试据此断言音色解析结果。 */
    internal val voice: String,
    private val silenceDurationMs: Long,
    /** 百炼业务空间 ID；非空时请求额外携带 `X-DashScope-WorkSpace` 头兜底。 */
    private val workspaceId: String?,
    private val webSocketClient: OkHttpClient,
) : Model {
    override val name: String = "qwen-audio-realtime:$model"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flow { throw UnsupportedOperationException("Realtime 模型不提供非实时文本调用") }

    override suspend fun connect(request: LlmRequest): LiveConnection {
        val instructions = request.config?.systemInstruction?.parts.orEmpty()
            .filter { it.text != null }.joinToString("") { it.text.orEmpty() }
        val tools = request.config?.tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
        val kernel = VoiceWsKernel(
            client = webSocketClient,
            request = QwenRealtimeProtocol.connectRequest(endpoint, model, apiKey, workspaceId),
            handshakeFailure = ::voiceHandshakeFailure,
        )
        return Connection(kernel, instructions, tools)
    }

    private inner class Connection(
        private val kernel: VoiceWsKernel,
        private val instructions: String,
        private val toolDeclarations: List<FunctionDeclaration>,
    ) : LiveConnection, RealtimeFunctionCallSink {
        private val collecting = Mutex()

        /** `session.updated` 确认前的上行缓冲；满则丢最旧，确认后一次冲出。 */
        private val pendingAudio = ArrayDeque<ByteArray>()
        private var pendingAudioBytes = 0

        @Volatile private var sessionConfigured = false

        /** 静默窗口：用户开始说话后置位，丢弃残余音频增量直到下一个响应开始。 */
        @Volatile private var suppressAudio = false

        /** 本条响应内是否出现过助手转写增量；`transcript.done` 防重复用。 */
        @Volatile private var sawTranscriptDelta = false

        /** 服务端 function_call 参数累积；仅接收循环访问。 */
        private val toolCalls = RealtimeToolCallBuffer()

        override suspend fun sendHistory(history: List<Content>) {
            // 全双工会话的上下文由服务端会话自身持有；历史注入留阶段二（Chat 原生化）。
        }

        override suspend fun sendContent(content: Content, partial: Boolean) {
            throw UnsupportedOperationException("Realtime 全双工会话不接受文本轮输入；用户输入以音频直喂")
        }

        override suspend fun sendRealtime(input: RealtimeInput) {
            when (input) {
                is RealtimeInput.Audio -> {
                    require(input.blob.mimeType == "audio/pcm;rate=16000") { "Expected mono PCM16 at 16 kHz" }
                    val bytes = input.blob.data ?: return
                    if (!sessionConfigured) {
                        bufferAudio(bytes)
                    } else {
                        sendAudio(bytes)
                    }
                }
                // 判轮归服务端 server_vad；本地 VAD 边界只驱动仓储侧的门控与即时停播。
                RealtimeInput.ActivityStart, RealtimeInput.ActivityEnd, RealtimeInput.AudioStreamEnd -> Unit
                is RealtimeInput.Video -> throw UnsupportedOperationException("Realtime 会话不接受视频输入")
            }
        }

        override fun receive(): Flow<LlmResponse> = flow {
            check(collecting.tryLock()) { "Only one live receiver is allowed" }
            try {
                val frames = kernel.frames()
                while (true) {
                    val frame = frames.receiveCatching().getOrNull() ?: break
                    when (frame) {
                        VoiceWsKernel.WsFrame.Opened -> {
                            // 工具是否真的随首个 session.update 下发，是排查「模型说没有工具」
                            // 的第一现场；重连即新会话，每次都要重新下发并留痕。
                            Log.i(TAG, "Qwen session.update 下发 ${toolDeclarations.size} 个工具声明")
                            kernel.sendControl(
                                QwenRealtimeProtocol.sessionUpdate(instructions, voice, silenceDurationMs, toolDeclarations),
                            )
                        }
                        is VoiceWsKernel.WsFrame.Control ->
                            when (val event = QwenRealtimeProtocol.parse(frame.payload)) {
                                is QwenRealtimeEvent.SessionUpdated -> {
                                    sessionConfigured = true
                                    flushPendingAudio()
                                }
                                is QwenRealtimeEvent.ResponseCreated -> {
                                    suppressAudio = false
                                    sawTranscriptDelta = false
                                }
                                is QwenRealtimeEvent.SpeechStarted -> {
                                    // 服务端自动打断生成；本地进入静默窗口并通知编排层立即停播。
                                    // 未定稿的工具调用随打断丢弃，防止残留泄入后续响应的兜底冲出。
                                    suppressAudio = true
                                    toolCalls.discardUnfinished()
                                    emit(LlmResponse(interrupted = true))
                                }
                                is QwenRealtimeEvent.AudioDelta -> {
                                    if (!suppressAudio) {
                                        emit(
                                            LlmResponse(
                                                content = Content(
                                                    role = Role.MODEL,
                                                    parts = listOf(
                                                        Part(
                                                            inlineData = Blob(
                                                                mimeType = "audio/pcm;rate=24000",
                                                                data = event.pcm24k,
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                                partial = true,
                                            ),
                                        )
                                    }
                                }
                                is QwenRealtimeEvent.AssistantTranscriptDelta -> {
                                    sawTranscriptDelta = true
                                    emit(
                                        LlmResponse(
                                            outputTranscription = Transcription(text = event.text, finished = false),
                                            partial = true,
                                        ),
                                    )
                                }
                                is QwenRealtimeEvent.AssistantTranscriptDone ->
                                    // 追加语义下终稿与增量重复；仅在整条响应无增量时补发。
                                    if (!sawTranscriptDelta && event.transcript.isNotEmpty()) {
                                        emit(
                                            LlmResponse(
                                                outputTranscription = Transcription(text = event.transcript, finished = false),
                                                partial = true,
                                            ),
                                        )
                                    }
                                is QwenRealtimeEvent.UserTranscriptDelta ->
                                    emit(
                                        LlmResponse(
                                            inputTranscription = Transcription(text = event.text, finished = false),
                                        ),
                                    )
                                is QwenRealtimeEvent.UserTranscript ->
                                    emit(
                                        LlmResponse(
                                            inputTranscription = Transcription(text = event.transcript, finished = true),
                                        ),
                                    )
                                is QwenRealtimeEvent.TurnCommitted ->
                                    emit(LlmResponse(customMetadata = mapOf(VoiceAudioMarkers.TURN_COMMITTED to true)))
                                is QwenRealtimeEvent.ToolCallStarted -> toolCalls.onStarted(event.callId, event.name)
                                is QwenRealtimeEvent.ToolCallArgumentsDelta -> toolCalls.onDelta(event.callId, event.delta)
                                is QwenRealtimeEvent.ToolCallArgumentsDone ->
                                    toolCalls.onDone(event.callId, event.arguments)?.let { emit(functionCallResponse(it)) }
                                is QwenRealtimeEvent.ResponseDone -> {
                                    // 仅明确取消/失败/截断的响应丢弃未定稿调用；status 缺失
                                    // 与未知终态按完成兜底冲出（后者记日志），避免静默漏执行。
                                    when (realtimeResponseDisposition(event.status)) {
                                        RealtimeResponseDisposition.DISCARD -> toolCalls.discardUnfinished()
                                        RealtimeResponseDisposition.UNKNOWN -> {
                                            Log.w(TAG, "未知的 response.done 状态，按完成处理: ${event.status}")
                                            for (call in toolCalls.flushUnfinished()) emit(functionCallResponse(call))
                                        }
                                        RealtimeResponseDisposition.FLUSH ->
                                            for (call in toolCalls.flushUnfinished()) emit(functionCallResponse(call))
                                    }
                                    emit(LlmResponse(turnComplete = true))
                                }
                                is QwenRealtimeEvent.ProtocolError ->
                                    emit(
                                        LlmResponse(
                                            errorCode = event.code,
                                            errorMessage = event.message,
                                        ),
                                    )
                                QwenRealtimeEvent.Ignored -> Unit
                            }
                        is VoiceWsKernel.WsFrame.Failure ->
                            // 传输失败以异常终止事件流：编排层据此按异常类型分类
                            // （握手鉴权失败不重试，网络失败按退避重连）。
                            throw frame.error
                        is VoiceWsKernel.WsFrame.Audio -> Unit
                        VoiceWsKernel.WsFrame.Closed -> break
                    }
                }
            } finally {
                collecting.unlock()
            }
        }

        override suspend fun closeSession() {
            kernel.close()
        }

        private fun bufferAudio(bytes: ByteArray) {
            pendingAudio.addLast(bytes)
            pendingAudioBytes += bytes.size
            while (pendingAudioBytes > MAX_PENDING_AUDIO_BYTES) {
                pendingAudio.removeFirstOrNull()?.let { pendingAudioBytes -= it.size }
            }
        }

        private suspend fun flushPendingAudio() {
            val buffered = pendingAudio.toList()
            pendingAudio.clear()
            pendingAudioBytes = 0
            buffered.forEach { sendAudio(it) }
        }

        private suspend fun sendAudio(bytes: ByteArray) {
            kernel.sendControl(QwenRealtimeProtocol.appendAudio(bytes))
        }

        /** 把一次定稿的 function_call 上抛为 ADK 事件；执行与写回由门面负责。 */
        private fun functionCallResponse(call: RealtimeToolCallBuffer.CompletedCall): LlmResponse = LlmResponse(
            content = Content(
                role = Role.MODEL,
                parts = listOf(
                    Part(
                        functionCall = FunctionCall(
                            name = call.name,
                            args = parseFunctionArguments(call.arguments),
                            id = call.callId,
                        ),
                    ),
                ),
            ),
        )

        override suspend fun sendFunctionResult(callId: String, outputJson: String) {
            runCatching { kernel.sendControl(QwenRealtimeProtocol.functionCallOutput(callId, outputJson)) }
                .onFailure { Log.w(TAG, "写回工具结果失败（连接可能已断开）", it) }
        }

        override suspend fun sendResponseTrigger() {
            runCatching { kernel.sendControl(QwenRealtimeProtocol.createResponse()) }
                .onFailure { Log.w(TAG, "触发工具二轮响应失败（连接可能已断开）", it) }
        }
    }

    private companion object {
        const val TAG = "QwenRealtimeModel"

        /** 配置确认前的上行缓冲上限；约 8 秒 16 kHz PCM16，超出丢最旧。 */
        const val MAX_PENDING_AUDIO_BYTES = 256 * 1024
    }
}
