@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess.openai.realtime

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
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.voice.repository.RealtimeInstructionSink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeFunctionCallSink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeTurnControlSink
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioMarkers
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeManualInputSink
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeResponseDisposition
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeToolCallBuffer
import github.ponyhuang.gimi.data.modelaccess.shared.parseFunctionArguments
import github.ponyhuang.gimi.data.modelaccess.shared.realtimeResponseDisposition
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * OpenAI Realtime 原生语音模型（GA 协议）。
 *
 * 与 [CascadedVoiceModel] 的分轮级联不同，这里是一条**常驻全双工会话**：
 * `connect` 建立 wss 并下发 `session.update`（人设/音色/手动判轮/输入转写，
 * 实时助手模式另携带 function 工具声明），上行 [RealtimeInput.Audio] 直接进
 * `input_audio_buffer.append`，本地 VAD 切句后经 [RealtimeTurnControlSink] 依次
 * 提交音频并请求响应，下行事件流持续产出音频块与双向转写。
 *
 * 契约要点：
 * - 文本通过 [LlmResponse.inputTranscription] 旁路承载用户侧转写（finished），
 *   [LlmResponse.outputTranscription] 承载助手侧转写增量；与级联的字幕通道一致。
 * - 判轮提交以 [VoiceAudioMarkers.TURN_COMMITTED] 标记事件下发，无文本/音频负载。
 * - 打断：本地 VAD 开口时发送 `response.cancel`，并补发
 *   `conversation.item.truncate`（以已发出时长近似截断点），清理服务端上下文里
 *   未播放的音频与转写；残余增量在下一个 `response.created` 前被抑制。
 * - 工具：function 工具声明随 `session.update` 下发；服务端的 function_call 经
 *   参数增量/完成事件定稿后，以 [LlmResponse] 的 functionCall part 上抛，由门面
 *   执行并经 [RealtimeFunctionCallSink] 写回结果、触发二轮推理。
 * - 音频上行在 `session.updated` 确认前进入小缓冲（满则丢最旧），确认后一次冲出；
 *   重连即新会话（上下文不重放），由编排层控制退避。
 */
internal class OpenAiRealtimeModel(
    private val endpoint: String,
    private val model: String,
    private val apiKey: String,
    /** 会话音色；会话首个 session.update 下发，测试据此断言音色解析结果。 */
    internal val voice: String,
    private val webSocketClient: OkHttpClient,
) : Model {
    override val name: String = "openai-realtime:$model"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flow { throw UnsupportedOperationException("Realtime 模型不提供非实时文本调用") }

    override suspend fun connect(request: LlmRequest): LiveConnection {
        val instructions = request.config.systemInstruction?.parts.orEmpty()
            .filter { it.text != null }.joinToString("") { it.text.orEmpty() }
        val tools = request.config.tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
        val kernel = VoiceWsKernel(
            client = webSocketClient,
            request = OpenAiRealtimeProtocol.connectRequest(endpoint, model, apiKey),
            handshakeFailure = ::voiceHandshakeFailure,
        )
        return Connection(kernel, instructions, tools)
    }

    private inner class Connection(
        private val kernel: VoiceWsKernel,
        private val instructions: String,
        private val toolDeclarations: List<FunctionDeclaration>,
    ) : LiveConnection, RealtimeInstructionSink, RealtimeFunctionCallSink, RealtimeTurnControlSink, RealtimeManualInputSink {
        private val collecting = Mutex()

        /** 串行化音频、提交、取消与响应创建，避免协程间发送顺序交叉。 */
        private val sendMutex = Mutex()

        /** 仅在同一次 WebSocket 会话的 `session.updated` 后完成。 */
        private val sessionReady = CompletableDeferred<Unit>()

        @Volatile private var suppressManualResponse = false
        @Volatile private var suppressAudio = false
        @Volatile private var responseActive = false

        /** 已发出 response.create、但尚未收到 response.created。 */
        @Volatile private var responseRequested = false

        /** 请求确认前发生打断；created 到达时必须补发取消。 */
        @Volatile private var cancelPending = false

        /** 手动输入的 response.create 已发出，但旧响应的取消收尾不得抹掉这帧请求。 */
        @Volatile private var manualResponseRequested = false
        override val manualInputReady: Boolean get() = sessionConfigured

        override suspend fun submitUserContent(content: Content) {
            check(sessionConfigured)
            val message = OpenAiRealtimeProtocol.userMessage(content, "manual_" + java.util.UUID.randomUUID().toString().replace("-", "").take(24))
            sendMutex.withLock {
                suppressManualResponse = true
                if (responseActive || responseRequested) {
                    val awaitingCreated = responseRequested
                    responseActive = false
                    responseRequested = false
                    suppressAudio = true
                    if (awaitingCreated) {
                        cancelPending = true
                    } else {
                        cancellationAwareRunCatching { kernel.sendControl(OpenAiRealtimeProtocol.cancelResponse()) }
                            .onFailure { Log.w(TAG, "取消手动输入前的响应失败", it) }
                    }
                }
                assistantItemId?.let { itemId ->
                    // 截断点取自与事件循环共享的字段：done(cancelled) 可能在 sendControl 挂起
                    // 间隙把复位跳过或推进，携带旧 itemId/字节数会被服务端拒绝；
                    // 与 interruptResponse 一致按失败降级处理，不让整通通话 Failed。
                    cancellationAwareRunCatching {
                        kernel.sendControl(OpenAiRealtimeProtocol.truncate(itemId, assistantAudioBytes / 48L))
                    }.onFailure { Log.w(TAG, "manual truncate rejected", it) }
                }
                kernel.sendControl(com.google.gson.JsonObject().apply { addProperty("type", "input_audio_buffer.clear") })
                kernel.sendControl(message)
                kernel.sendControl(OpenAiRealtimeProtocol.createResponse())
                responseRequested = true
                manualResponseRequested = true
            }
        }

        /** `session.updated` 确认前的上行缓冲；满则丢最旧，确认后一次冲出。 */
        private val pendingAudio = ArrayDeque<ByteArray>()
        private var pendingAudioBytes = 0

        /** `session.updated` 确认前的历史消息缓冲；确认后在音频前一次冲出。 */
        private val pendingHistory = mutableListOf<com.google.gson.JsonObject>()

        @Volatile private var sessionConfigured = false

        /** 当前助手消息 item id；截断事件需要它。 */
        @Volatile private var assistantItemId: String? = null

        /** 当前响应已下发的音频字节数；近似「用户已听到」的截断点，本地打断线程也会读取。 */
        @Volatile private var assistantAudioBytes = 0

        /** 服务端 function_call 参数累积；仅接收循环访问。 */
        private val toolCalls = RealtimeToolCallBuffer()

        override suspend fun updateInstructions(instructions: String) {
            check(sessionConfigured)
            sendMutex.withLock { kernel.sendControl(OpenAiRealtimeProtocol.instructionsUpdate(instructions)) }
        }

        override suspend fun commitAudioTurn() {
            sessionReady.await()
            sendMutex.withLock {
                for (event in OpenAiRealtimeProtocol.commitAudioTurn()) kernel.sendControl(event)
                responseRequested = true
            }
        }

        override suspend fun cancelResponse() {
            sendMutex.withLock {
                when {
                    responseActive -> cancelActiveResponseLocked()
                    responseRequested -> {
                        responseRequested = false
                        cancelPending = true
                        suppressAudio = true
                    }
                }
            }
        }

        override suspend fun sendHistory(history: List<Content>) {
            val events = OpenAiRealtimeProtocol.historyMessages(history)
            if (events.isEmpty()) return
            sendMutex.withLock {
                if (!sessionConfigured) {
                    pendingHistory.addAll(events)
                } else {
                    for (event in events) {
                        kernel.sendControl(event)
                    }
                }
            }
        }

        override suspend fun sendContent(content: Content, partial: Boolean) {
            throw UnsupportedOperationException("Realtime 全双工会话不接受文本轮输入；用户输入以音频直喂")
        }

        override suspend fun sendRealtime(input: RealtimeInput) {
            when (input) {
                is RealtimeInput.Audio -> {
                    require(input.blob.mimeType == "audio/pcm;rate=24000") { "Expected mono PCM16 at 24 kHz" }
                    val bytes = input.blob.data ?: return
                    sendMutex.withLock {
                        if (!sessionConfigured) {
                            bufferAudio(bytes)
                        } else {
                            sendAudio(bytes)
                        }
                    }
                }
                // 手动判轮由 RealtimeTurnControlSink 显式提交；ADK 活动边界不映射协议事件。
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
                        VoiceWsKernel.WsFrame.Opened ->
                            kernel.sendControl(
                                OpenAiRealtimeProtocol.sessionUpdate(instructions, voice, toolDeclarations),
                            )
                        is VoiceWsKernel.WsFrame.Control ->
                            when (val event = OpenAiRealtimeProtocol.parse(frame.payload)) {
                                OpenAiRealtimeEvent.ResponseStarted -> sendMutex.withLock {
                                    responseActive = !cancelPending
                                    if (cancelPending) {
                                        cancelPending = false
                                        suppressAudio = true
                                        cancellationAwareRunCatching {
                                            kernel.sendControl(OpenAiRealtimeProtocol.cancelResponse())
                                        }.onFailure { Log.w(TAG, "取消延迟确认的响应失败", it) }
                                    } else {
                                        responseRequested = false
                                        manualResponseRequested = false
                                        suppressManualResponse = false
                                        suppressAudio = false
                                    }
                                }
                                is OpenAiRealtimeEvent.SessionUpdated -> sendMutex.withLock {
                                    sessionConfigured = true
                                    flushPendingHistory()
                                    flushPendingAudio()
                                    sessionReady.complete(Unit)
                                }
                                is OpenAiRealtimeEvent.AudioDelta -> {
                                    if (suppressManualResponse || suppressAudio) continue
                                    assistantAudioBytes += event.pcm24k.size
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
                                is OpenAiRealtimeEvent.AssistantTranscriptDelta -> if (!suppressManualResponse && !suppressAudio)
                                    emit(
                                        LlmResponse(
                                            outputTranscription = Transcription(text = event.text, finished = false),
                                            partial = true,
                                        ),
                                    )
                                is OpenAiRealtimeEvent.UserTranscript ->
                                    emit(
                                        LlmResponse(
                                            inputTranscription = Transcription(text = event.transcript, finished = true),
                                        ),
                                    )
                                is OpenAiRealtimeEvent.TurnCommitted ->
                                    emit(LlmResponse(customMetadata = mapOf(VoiceAudioMarkers.TURN_COMMITTED to true)))
                                is OpenAiRealtimeEvent.AssistantItemStarted -> assistantItemId = event.itemId
                                is OpenAiRealtimeEvent.SpeechStarted -> {
                                    // 打断即取消生成：未定稿的工具调用随打断丢弃，防止残留
                                    // 泄入后续响应的兜底冲出（以截断参数执行或为取消调用写回）。
                                    toolCalls.discardUnfinished()
                                    sendMutex.withLock { interruptResponse() }
                                }
                                is OpenAiRealtimeEvent.ToolCallStarted -> toolCalls.onStarted(event.callId, event.name)
                                is OpenAiRealtimeEvent.ToolCallArgumentsDelta -> toolCalls.onDelta(event.callId, event.delta)
                                is OpenAiRealtimeEvent.ToolCallArgumentsDone ->
                                    toolCalls.onDone(event.callId, event.arguments)
                                        ?.takeUnless { suppressManualResponse || suppressAudio }
                                        ?.let { emit(functionCallResponse(it)) }
                                is OpenAiRealtimeEvent.ResponseDone -> {
                                    val disposition = realtimeResponseDisposition(event.status)
                                    sendMutex.withLock {
                                        responseActive = false
                                        responseRequested = responseRequested && manualResponseRequested
                                        // done 只属于更早的响应（done 必然晚于它自己的 created），
                                        // 不得否决针对未来 created 的待取消——否则打断丢失、旧回答续播。
                                        suppressAudio = suppressAudio || disposition == RealtimeResponseDisposition.DISCARD
                                    }
                                    if (suppressManualResponse) {
                                        toolCalls.discardUnfinished()
                                        continue
                                    }
                                    if (suppressAudio) {
                                        toolCalls.discardUnfinished()
                                    } else {
                                        // 仅明确取消/失败/截断的响应丢弃未定稿调用；status 缺失
                                        // 与未知终态按完成兜底冲出（后者记日志），避免静默漏执行。
                                        when (disposition) {
                                            RealtimeResponseDisposition.DISCARD -> toolCalls.discardUnfinished()
                                            RealtimeResponseDisposition.UNKNOWN -> {
                                                Log.w(TAG, "未知的 response.done 状态，按完成处理: ${event.status}")
                                                for (call in toolCalls.flushUnfinished()) emit(functionCallResponse(call))
                                            }
                                            RealtimeResponseDisposition.FLUSH ->
                                                for (call in toolCalls.flushUnfinished()) emit(functionCallResponse(call))
                                        }
                                    }
                                    assistantItemId = null
                                    assistantAudioBytes = 0
                                    emit(LlmResponse(turnComplete = true))
                                }
                                is OpenAiRealtimeEvent.ProtocolError ->
                                    emit(
                                        LlmResponse(
                                            errorCode = event.code,
                                            errorMessage = event.message,
                                        ),
                                    )
                                OpenAiRealtimeEvent.Ignored -> Unit
                            }
                        is VoiceWsKernel.WsFrame.Failure -> {
                            // 传输失败以异常终止事件流：编排层据此按异常类型分类
                            // （握手鉴权失败不重试，网络失败按退避重连）。
                            sessionReady.completeExceptionally(frame.error)
                            throw frame.error
                        }
                        is VoiceWsKernel.WsFrame.Audio -> Unit
                        VoiceWsKernel.WsFrame.Closed -> {
                            sessionReady.completeExceptionally(IllegalStateException("Realtime 会话在配置确认前关闭"))
                            break
                        }
                    }
                }
            } finally {
                sessionReady.completeExceptionally(IllegalStateException("Realtime 接收循环在配置确认前结束"))
                collecting.unlock()
            }
        }

        override suspend fun closeSession() {
            sessionReady.completeExceptionally(IllegalStateException("Realtime 会话已关闭"))
            kernel.close()
        }

        private fun bufferAudio(bytes: ByteArray) {
            pendingAudio.addLast(bytes)
            pendingAudioBytes += bytes.size
            while (pendingAudioBytes > MAX_PENDING_AUDIO_BYTES) {
                pendingAudio.removeFirstOrNull()?.let { pendingAudioBytes -= it.size }
            }
        }

        private suspend fun flushPendingHistory() {
            val buffered = pendingHistory.toList()
            pendingHistory.clear()
            for (event in buffered) {
                kernel.sendControl(event)
            }
        }

        private suspend fun flushPendingAudio() {
            val buffered = pendingAudio.toList()
            pendingAudio.clear()
            pendingAudioBytes = 0
            buffered.forEach { sendAudio(it) }
        }

        private suspend fun sendAudio(bytes: ByteArray) {
            kernel.sendControl(OpenAiRealtimeProtocol.appendAudio(bytes))
        }

        /** 调用方持有 [sendMutex]；工具累积器仍只由接收循环维护。 */
        private suspend fun cancelActiveResponseLocked() {
            responseActive = false
            responseRequested = false
            suppressAudio = true
            cancellationAwareRunCatching { kernel.sendControl(OpenAiRealtimeProtocol.cancelResponse()) }
                .onFailure { Log.w(TAG, "取消本地打断响应失败（连接可能已断开）", it) }
            interruptResponse()
        }

        /**
         * 打断后补发截断，移除服务端上下文中未播放的音频与其转写。截断点用
         * 「已发出时长」近似「已播放时长」——只可能偏大，满足服务端「不得超过
         * 实际时长」的约束。
         */
        private suspend fun interruptResponse() {
            val itemId = assistantItemId ?: return
            val audioEndMs = assistantAudioBytes * 1000L / (AUDIO_SAMPLE_RATE_HZ * 2L)
            cancellationAwareRunCatching { kernel.sendControl(OpenAiRealtimeProtocol.truncate(itemId, audioEndMs)) }
                .onFailure { Log.w(TAG, "发送截断失败（连接可能已断开）", it) }
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
            sendMutex.withLock {
                cancellationAwareRunCatching { kernel.sendControl(OpenAiRealtimeProtocol.functionCallOutput(callId, outputJson)) }
                    .onFailure { Log.w(TAG, "写回工具结果失败（连接可能已断开）", it) }
            }
        }

        override suspend fun sendResponseTrigger() {
            sendMutex.withLock {
                cancellationAwareRunCatching { kernel.sendControl(OpenAiRealtimeProtocol.createResponse()) }
                    .onFailure { Log.w(TAG, "触发工具二轮响应失败（连接可能已断开）", it) }
                    .onSuccess { responseRequested = true }
            }
        }
    }

    private companion object {
        const val TAG = "OpenAiRealtimeModel"
        const val AUDIO_SAMPLE_RATE_HZ = 24_000L

        /** 配置确认前的上行缓冲上限；约 5 秒 24 kHz PCM16，超出丢最旧。 */
        const val MAX_PENDING_AUDIO_BYTES = 256 * 1024
    }
}
