package github.ponyhuang.gimi.data.modelaccess.minimax.speech

import com.google.gson.JsonObject
import github.ponyhuang.gimi.core.audio.VoiceAudioFormat
import github.ponyhuang.gimi.core.network.VoiceWsKernel
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSegment
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSessionRequest
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

/**
 * MiniMax 双向流式语音合成。
 *
 * 一条 WebSocket 连接承载一次合成会话：客户端用 `task_continue` 逐次投递文本，
 * 服务端按标点切句、边合成边下发音频；`task_cancel` 让服务端丢弃尚未合成的文本并停止下发，
 * 实现**协议级打断**——这与"客户端单方面断开、服务端继续合成并计费"有本质区别。
 *
 * 与一次性 HTTP 合成（[MinimaxSpeechProtocol.synthesize]）的差别在首包时机：
 * HTTP 必须拿到完整文本才能开始合成，而这里只收到半句就可以开始。
 */
class MinimaxBidiSynthesizer(
    private val okHttpClient: OkHttpClient,
) : StreamingSpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(MINIMAX_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    /** 端点跟随服务配置的基址，用户改一次接入地址即可整体切换区域。 */
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.ConfiguredBaseUrl

    override fun open(request: SynthesisSessionRequest): SpeechSynthesisSession =
        MinimaxBidiSession(okHttpClient, request)
}

/**
 * 一次 MiniMax 双向流式合成会话。
 *
 * 内部有两条协程：一条读服务端事件并切分语音片段，一条按顺序投递客户端命令。
 * 客户端命令必须排在 `task_started` 之后（乱序会得到 2206 并被服务端断开连接），
 * 因此所有命令先进入队列，由投递协程在开窗确认后按序发出。
 *
 * 本会话只支持一次 flush：首个 `task_flushed` 会终结片段流（协议本身允许 flush 后
 * 继续 `task_continue`，逐句 flush 用法需要另开会话或改造）。
 */
internal class MinimaxBidiSession(
    client: OkHttpClient,
    private val request: SynthesisSessionRequest,
) : SpeechSynthesisSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val segmentQueue = Channel<SynthesisSegment>(Channel.UNLIMITED)
    private val started = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()
    private val flushLock = Mutex()
    private val kernel = VoiceWsKernel(client, buildRequest(), handshakeFailure = ::voiceHandshakeFailure)

    /** 本次 flush 的完成信号；服务端回 `task_flushed` 时兑现。 */
    @Volatile private var flushSignal: CompletableDeferred<Unit>? = null

    /** 会话失败原因；失败要让所有等待者知道，而不是让流静默结束。 */
    @Volatile private var failure: Throwable? = null

    @Volatile private var closed = false

    /** 已被调用方打断；收尾时不再等待服务端把剩余文本合成完。 */
    @Volatile private var cancelled = false

    init {
        scope.launch { pumpEvents() }
        scope.launch { sendCommands() }
    }

    override fun send(text: String) {
        if (closed || text.isEmpty()) return
        commands.trySend(Command.Text(text))
    }

    override suspend fun flush() = flushLock.withLock {
        check(!closed) { "语音合成会话已关闭" }
        // 会话已经失败时直接抛出：否则新建的等待信号永远不会被兑现。
        failure?.let { throw it }
        val signal = CompletableDeferred<Unit>()
        flushSignal = signal
        commands.send(Command.Flush)
        // 服务端确认无超时兜底会把调用方永久挂起（连接保活时读循环感知不到"半死"），
        // 超时按会话失败处理，由调用方关闭重开。限时在真实调度器上执行，
        // 避免调用方处于虚拟时间测试调度器时超时被提前触发。
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(FLUSH_TIMEOUT_MS.milliseconds) { signal.await() }
        } ?: throw IOException("等待语音合成 flush 确认超时")
    }

    override fun cancel() {
        if (closed) return
        cancelled = true
        commands.trySend(Command.Cancel)
    }

    override val segments: Flow<SynthesisSegment> = segmentQueue.consumeAsFlow()

    override suspend fun close() {
        if (closed) return
        closed = true
        if (cancelled) {
            // 打断后不再等服务端收尾：连接直接断开，否则打断会平白多等一个来回。
            kernel.close()
            scope.cancel()
            segmentQueue.close()
            return
        }
        // 让服务端把缓冲文本合成完再收尾。
        commands.trySend(Command.Finish)
        withTimeoutOrNull(CLOSE_TIMEOUT_MS.milliseconds) { finished.await() }
        kernel.close()
        scope.cancel()
        segmentQueue.close()
    }

    /** 读服务端事件：切分语音片段，并把失败翻译成调用方能处理的异常。 */
    private suspend fun pumpEvents() {
        var current: Segment? = null
        var cause: Throwable? = null
        try {
            for (frame in kernel.frames()) {
                when (frame) {
                    // 握手完成即开窗：官方示例也是连上就发 task_start，
                    // 不依赖 connected_success 才推进，避免服务端少发该事件时整段卡住。
                    is VoiceWsKernel.WsFrame.Opened -> kernel.sendControl(taskStart())
                    is VoiceWsKernel.WsFrame.Control -> {
                        val payload = frame.payload
                        ensureBaseRespOk(payload)
                        when (payload.eventName()) {
                            // 服务端在握手后先回的确认事件；开窗已经发出，这里只做登记。
                            EVENT_CONNECTED_SUCCESS -> Unit
                            EVENT_TASK_STARTED -> started.complete(Unit)
                            EVENT_SENTENCE_START -> current = openSegment()
                            EVENT_TASK_CONTINUED -> current = deliverAudio(current, payload)
                            EVENT_SENTENCE_END -> {
                                current?.close()
                                current = null
                            }
                            EVENT_TASK_CANCELED -> {
                                // 被取消的这一句不再交付：音频已经下发也无法撤回，交给播放层丢弃。
                                current?.close()
                                current = null
                            }
                            EVENT_TASK_FLUSHED -> {
                                flushSignal?.complete(Unit)
                                // 服务端确认本轮音频已全部下发，此后不会再有片段：结束片段流。
                                segmentQueue.close()
                            }
                            EVENT_TASK_FINISHED -> {
                                current?.close()
                                current = null
                                finished.complete(Unit)
                            }
                            EVENT_TASK_FAILED -> throw payload.toTaskException()
                            else -> Unit
                        }
                    }
                    // MiniMax 的音频走 JSON 里的十六进制负载，不使用二进制帧。
                    is VoiceWsKernel.WsFrame.Audio -> Unit
                    is VoiceWsKernel.WsFrame.Failure -> throw frame.error
                    is VoiceWsKernel.WsFrame.Closed -> {
                        current?.close()
                        current = null
                        break
                    }
                }
            }
        } catch (error: Throwable) {
            cause = error
        } finally {
            current?.close()
            failure = cause
            val terminal = cause ?: IOException("语音合成会话提前结束")
            if (!started.isCompleted) started.completeExceptionally(terminal)
            flushSignal?.completeExceptionally(terminal)
            segmentQueue.close()
            finished.complete(Unit)
        }
    }

    /** 按顺序投递客户端命令；必须等开窗确认，否则服务端会以 2206 断开连接。 */
    private suspend fun sendCommands() {
        try {
            withTimeoutOrNull(STARTED_TIMEOUT_MS.milliseconds) { started.await() }
                ?: run {
                    // 握手确认超时：服务端半死。记录失败并断开连接，
                    // 读事件协程会感知连接关闭并终结整个会话。
                    recordFailure(IOException("等待语音合成任务启动超时"))
                    kernel.close()
                    return
                }
        } catch (_: Throwable) {
            // 会话已经失败：失败原因由读事件的协程交给调用方，这里静默退出，
            // 否则它会作为未捕获异常冒泡成进程级错误。
            return
        }
        try {
            for (command in commands) {
                when (command) {
                    is Command.Text -> kernel.sendControl(taskContinue(command.text))
                    Command.Flush -> kernel.sendControl(taskFlush())
                    Command.Cancel -> kernel.sendControl(taskCancel())
                    Command.Finish -> kernel.sendControl(taskFinish())
                }
            }
        } catch (error: CancellationException) {
            // close() 取消本 scope 属正常收尾，按取消语义原样传播即可。
            throw error
        } catch (error: Throwable) {
            // 连接已断等发送失败不能逃出这个无 handler 的 scope 协程，
            // 否则会冒泡成进程级崩溃；记录原因并交给所有等待者。
            recordFailure(error)
        }
    }

    /** 记录会话失败原因并唤醒 flush 等待者；读事件协程的 finally 会兜底收尾。 */
    private fun recordFailure(error: Throwable) {
        if (failure == null) failure = error
        flushSignal?.completeExceptionally(error)
    }

    /** 开一个新片段并交给调用方；片段的音频随服务端下发即时到达。 */
    private fun openSegment(): Segment {
        val segment = Segment()
        val delivered = segmentQueue.trySend(SynthesisSegment(segment.text, segment.chunks.consumeAsFlow()))
        if (delivered.isFailure) throw IOException("语音合成会话已结束")
        return segment
    }

    /** 把一块音频与字幕并入当前片段；服务端偶尔不带 `sentence_start`，此时就地开一个片段。 */
    private suspend fun deliverAudio(current: Segment?, payload: JsonObject): Segment {
        val segment = current ?: openSegment()
        val data = payload.getAsJsonObject("data")
        // 先落文本再送音频：消费方拿到音频时，字幕已经就绪。
        val text = data?.getAsJsonObject("subtitle")
            ?.get("text")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (text.isNotBlank()) segment.text.value = text
        val hex = data?.get("audio")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (hex.isNotEmpty()) segment.chunks.send(decodeAudioHex(hex))
        return segment
    }

    private fun buildRequest(): Request = Request.Builder()
        .url(MiniMaxEndpoints.wsBidi(request.baseUrl))
        .header("Authorization", "Bearer ${request.apiKey}")
        .build()

    /** 开窗事件：锁定音色、模型与音频格式，并要求服务端给出片段字幕。 */
    private fun taskStart(): JsonObject = JsonObject().apply {
        addProperty("event", EVENT_TASK_START)
        addProperty("model", request.modelId)
        addProperty("language_boost", "auto")
        add(
            "voice_setting",
            JsonObject().apply {
                addProperty("voice_id", request.voiceId)
                addProperty("speed", 1.0)
                addProperty("vol", 1.0)
                addProperty("pitch", 0)
            },
        )
        add(
            "audio_setting",
            JsonObject().apply {
                // 24 kHz 单声道 PCM 与播放器的 AudioTrack 一致，服务端不需要重采样，首包即可播。
                addProperty("sample_rate", VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ)
                addProperty("format", AUDIO_FORMAT_PCM)
                addProperty("channel", VoiceAudioFormat.CHANNEL_COUNT)
            },
        )
        // 片段字幕让播放确认与字幕有据可依；没有它服务端只给音频，文本得由调用方自己配对。
        addProperty("subtitle_enable", true)
        addProperty("subtitle_type", SUBTITLE_TYPE_SENTENCE)
    }

    private fun taskContinue(text: String): JsonObject = JsonObject().apply {
        addProperty("event", EVENT_TASK_CONTINUE)
        addProperty("text", text)
    }

    private fun taskFlush(): JsonObject = JsonObject().apply {
        addProperty("event", EVENT_TASK_FLUSH)
    }

    private fun taskCancel(): JsonObject = JsonObject().apply {
        addProperty("event", EVENT_TASK_CANCEL)
    }

    private fun taskFinish(): JsonObject = JsonObject().apply {
        addProperty("event", EVENT_TASK_FINISH)
    }

    /** 服务端事件的类型名。 */
    private fun JsonObject.eventName(): String =
        get("event")?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    /** 业务错误码在每条服务端消息里；非零即失败，且失败发生在音频解码之前。 */
    private fun ensureBaseRespOk(payload: JsonObject) {
        val base = payload.getAsJsonObject("base_resp") ?: return
        val statusCode = base.get("status_code")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        if (statusCode == 0) return
        throw TtsSynthesisException(
            statusCode,
            baseRespMessage(statusCode),
            base.get("status_msg")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        )
    }

    /** `task_failed` 是会话级致命错误：服务端随后会关闭连接，调用方必须重开会话。 */
    private fun JsonObject.toTaskException(): TtsSynthesisException {
        val base = getAsJsonObject("base_resp")
        val statusCode = base?.get("status_code")?.takeUnless { it.isJsonNull }?.asInt ?: 1000
        val statusMessage = base?.get("status_msg")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        return TtsSynthesisException(statusCode, baseRespMessage(statusCode), statusMessage)
    }

    /** 一个语音片段：文本随字幕到达，音频随合成进度到达。 */
    private class Segment {
        val text = MutableStateFlow("")
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)

        fun close() {
            chunks.close()
        }
    }

    /** 待投递的客户端命令；顺序即投递顺序。 */
    private sealed interface Command {
        data class Text(val text: String) : Command
        data object Flush : Command
        data object Cancel : Command
        data object Finish : Command
    }

    private companion object {
        const val EVENT_TASK_START = "task_start"
        const val EVENT_TASK_CONTINUE = "task_continue"
        const val EVENT_TASK_FLUSH = "task_flush"
        const val EVENT_TASK_CANCEL = "task_cancel"
        const val EVENT_TASK_FINISH = "task_finish"

        /** 服务端在握手后先回的确认事件。 */
        const val EVENT_CONNECTED_SUCCESS = "connected_success"
        const val EVENT_TASK_STARTED = "task_started"
        const val EVENT_SENTENCE_START = "sentence_start"
        const val EVENT_TASK_CONTINUED = "task_continued"
        const val EVENT_SENTENCE_END = "sentence_end"
        const val EVENT_TASK_CANCELED = "task_canceled"
        const val EVENT_TASK_FLUSHED = "task_flushed"
        const val EVENT_TASK_FINISHED = "task_finished"
        const val EVENT_TASK_FAILED = "task_failed"

        const val AUDIO_FORMAT_PCM = "pcm"
        const val SUBTITLE_TYPE_SENTENCE = "sentence"

        /** 收尾时等服务端确认的时长上限；超时直接断开，不让调用方无限等待。 */
        const val CLOSE_TIMEOUT_MS = 3_000L

        /** 等待 task_started 的上限；连接保活时服务端半死无法靠断连感知，必须超时兜底。 */
        const val STARTED_TIMEOUT_MS = 15_000L

        /** 等待 task_flushed 的上限；超时视为会话失败，由调用方关闭重开。 */
        const val FLUSH_TIMEOUT_MS = 10_000L
    }
}
