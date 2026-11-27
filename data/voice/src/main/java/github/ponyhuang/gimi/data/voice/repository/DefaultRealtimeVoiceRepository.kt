package github.ponyhuang.gimi.data.voice.repository

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.audio.AmbientNoiseFloorTracker
import github.ponyhuang.gimi.core.audio.PlaybackEchoGuard
import github.ponyhuang.gimi.core.audio.SileroVoiceActivityDetector
import github.ponyhuang.gimi.core.audio.SoftwareEchoCanceller
import github.ponyhuang.gimi.core.audio.VoiceActivitySegmenter
import github.ponyhuang.gimi.core.audio.VoiceAudioFormat
import github.ponyhuang.gimi.core.audio.UtteranceCommitGate
import github.ponyhuang.gimi.core.audio.UtteranceSegment
import github.ponyhuang.gimi.core.audio.VoiceAudioRecorder
import github.ponyhuang.gimi.core.audio.calculatePcm16Level
import github.ponyhuang.gimi.core.audio.encodePcm16Wav
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.data.voice.playback.RealtimePcmPlayer
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.voice.repository.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** 装配实时语音会话；录放音生命周期独立，文字历史是否共享由配置决定。 */
class DefaultRealtimeVoiceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backend: VoiceAudioBackend,
) : RealtimeVoiceRepository {
    override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession = Session(configuration)

    private inner class Session(private val configuration: RealtimeVoiceConfiguration) : RealtimeVoiceSession {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutableState = MutableStateFlow(RealtimeVoiceState())
        override val state: StateFlow<RealtimeVoiceState> = mutableState.asStateFlow()
        private val lifecycle = VoiceSessionLifecycle()
        private val recorder = VoiceAudioRecorder()
        private val player = RealtimePcmPlayer()
        // 级联会话仅级联路径使用;Realtime 模式走 openDuplex,惰性化避免构造期误开。
        private val session: VoiceAudioBackendSession by lazy { backend.open(configuration) }
        private val audio = context.getSystemService(AudioManager::class.java)
        private var detector: SileroVoiceActivityDetector? = null
        private var echoCanceller: SoftwareEchoCanceller? = null
        private var recognitionJob: Job? = null
        private var replyJob: Job? = null
        private var replyToFinalizeOnClose: Job? = null
        private var inputRevision = 0L
        private var lastInputLevelNanos = 0L
        private val inputLock = Any()
        private val closed: Boolean get() = lifecycle.closed
        @Volatile private var started = false
        @Volatile private var replyActive = false
        // 播放期回声门控：不依赖 AEC 是否真的消干净，两种模式共用同一份决策。
        private val echoGuard = PlaybackEchoGuard()
        // 当前采集帧对应的播放参考电平；录音线程在 inputLock 内写，detector 回调同线程读。
        private var currentReferenceLevel = 0f
        private var routingOwned = false
        private var previousMode = AudioManager.MODE_NORMAL

        // —— Realtime 模式(全双工)专属;级联模式全部保持 null/不激活 ——

        private val duplexMode = configuration.pipeline == VoicePipeline.NATIVE_REALTIME
        private var duplex: RealtimeVoiceDuplexSession? = null
        private var duplexJob: Job? = null
        private var duplexPlayback: Channel<ByteArray>? = null
        private var duplexPlaybackJob: Job? = null

        /** 等待服务端响应的兜底任务；超时后把卡住的 Thinking 拉回 Listening。 */
        private var responseWatchdog: Job? = null

        /** 上行门控的语音预滚环(约 12 帧/384ms),首帧放行前冲出,保证不丢前导音。 */
        private val uplinkPreRoll = ArrayDeque<ByteArray>()
        @Volatile private var uplinkGateOpen = false

        /**
         * 环境底噪跟踪:识别提交门的电平参照。与切句器同一线程喂入(reset 除外),
         * 电平刻度同 [calculatePcm16Level]。
         */
        private val noiseFloorTracker = AmbientNoiseFloorTracker()

        /**
         * 会话就绪前的上行暂存：openDuplex 为挂起语义（实时助手模式解析工具桥可能耗时），
         * 期间麦克风已开、duplex 仍为 null，帧在此暂存而非丢弃；会话就绪后按序发送。
         * [UplinkFrameQueue] 入队即拷贝（VAD 帧缓冲按帧复用），与 [duplex] 交换同锁访问。
         */
        private val uplinkQueue = UplinkFrameQueue(PENDING_UPLINK_FRAMES)

        /** 单一上行消费协程：所有帧（含会话就绪前的暂存）经同一队列按入队顺序发送。 */
        private var uplinkDrainJob: Job? = null
        private val routing = VoiceAudioRoutingController(audio, onState = { routing ->
            if (routing.currentDeviceId != state.value.audioRouting.currentDeviceId) {
                // 新声学路径需要重新收敛，先开保护窗口再重置，避免残留回声触发自打断。
                echoGuard.onAecReset()
                // 重置投递到 IO：主线程回调持 inputLock 会与录音线程的逐帧 DSP 争抢造成 jank
                scope.launch(Dispatchers.IO) {
                    synchronized(inputLock) {
                        echoCanceller?.reset()
                        detector?.reset()
                        segmenter.reset()
                        noiseFloorTracker.reset()
                    }
                }
            }
            mutableState.update { it.copy(audioRouting = routing) }
        })
        private val segmenter = VoiceActivitySegmenter(
            onSpeechStarted = {
                // 检测回调与上行帧同处录音线程：在派发主线程任务前同步推进 revision，
                // 并排入打断控制，避免旧 commit 与新一轮音频的顺序窗口。
                synchronized(inputLock) {
                    if (!closed && started && !state.value.muted && !state.value.inputPaused) {
                        inputRevision++
                        uplinkQueue.enqueueInterrupt()
                        ensureUplinkDrain()
                    }
                }
                scope.launch { speechStarted() }
            },
            onUtterance = { segment ->
                if (duplexMode) {
                    synchronized(inputLock) {
                        if (closed || !started || state.value.muted || state.value.inputPaused) return@synchronized
                        // 本地 VAD 独占判轮：提交控制与音频同队列，后面的新语音无法插到本轮前面。
                        uplinkGateOpen = false
                        uplinkPreRoll.clear()
                        uplinkQueue.enqueueCommit()
                        ensureUplinkDrain()
                    }
                } else {
                    if (configuration.pipeline == VoicePipeline.SEMI_CASCADED) {
                        scope.launch { handleSemiCascadedUtterance(segment) }
                    } else {
                        scope.launch { recognize(segment) }
                    }
                }
            },
        )
        // 通话由麦克风型前台服务维持。音频焦点变化只是建议性提示：后台被其他应用
        // 短暂抢占时关闭会话会造成意外中断，因此不注册焦点丢失监听，由服务持续持有麦克风。
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .build()

        @SuppressLint("MissingPermission")
        override suspend fun start() = withContext(Dispatchers.Main.immediate) {
            check(!closed)
            if (started) return@withContext
            var startupFailure = RealtimeVoiceFailure.Configuration
            cancellationAwareRunCatching {
                if (!duplexMode) {
                    check(session.isAvailable()) { "Speech configuration unavailable" }
                }
                startupFailure = RealtimeVoiceFailure.AudioProcessing
                withContext(NonCancellable + Dispatchers.IO) {
                    val created = SileroVoiceActivityDetector(context.assets)
                    synchronized(inputLock) {
                        if (closed) created.close() else {
                            detector = created
                            echoCanceller = SoftwareEchoCanceller()
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (closed) return@withContext
                check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                previousMode = audio.mode
                routingOwned = true
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                routing.start()
                started = true
                startupFailure = RealtimeVoiceFailure.Microphone
                check(recorder.start(
                    onAudioChunk = {},
                    onTimedAudioChunk = { bytes, firstTime ->
                        synchronized(inputLock) {
                            if (!closed) {
                                if (recorder.echoCancellationEnabled) {
                                    // 硬件 AEC 路径不跑软件 AEC，但仍要取播放参考电平做回声判定；
                                    // 设备声明支持 AEC 不等于对该 AudioTrack 真的生效。
                                    currentReferenceLevel = playbackReferenceLevel(bytes, firstTime)
                                    acceptCleanAudio(bytes)
                                } else {
                                    echoCanceller?.accept(bytes, firstTime, { time ->
                                        player.echoReference(time).also {
                                            currentReferenceLevel = calculatePcm16Level(it)
                                        }
                                    }, ::acceptCleanAudio)
                                }
                            }
                        }
                    },
                    onAudioLevel = {},
                    onError = { scope.launch { fail(RealtimeVoiceFailure.Microphone) } },
                    voiceCommunication = true,
                )) { "Microphone unavailable" }
                // 通话刚建立：AEC 滤波器从零开始收敛，同样给一段保护窗口。
                echoGuard.onAecReset()
                mutableState.update { it.copy(phase = RealtimeVoicePhase.Listening, listening = !it.muted && !it.inputPaused, failure = null) }
                if (duplexMode) startDuplex()
            }.onFailure { error ->
                android.util.Log.w(TAG, "Voice start failed", error)
                fail((error as? VoiceAudioBackendException)?.failure ?: startupFailure)
            }
            Unit
        }

        /** Realtime 模式:打开常驻全双工会话并启动服务端事件消费。 */
        private suspend fun startDuplex() {
            // openDuplex 为挂起语义：实时助手模式在此解析工具桥（MCP 探测走 IO 调度）。
            val session = backend.openDuplex(configuration)
            // 就绪即接管上行：解析窗口内暂存的帧已在同一个有界队列里，按入队顺序由单一
            // 协程发送；session.updated 确认前由连接层的 pendingAudio 继续小缓冲。
            // 首事件可能同步报错，先登记 Job，失败处理才能取消并等待这个收集者。
            duplexJob = scope.launch(start = CoroutineStart.LAZY) {
                cancellationAwareRunCatching {
                    session.events().collect { event -> handleDuplexEvent(event) }
                }.onFailure { error ->
                    android.util.Log.w(TAG, "Duplex event stream failed", error)
                    fail((error as? VoiceAudioBackendException)?.failure ?: RealtimeVoiceFailure.Generation)
                }
            }
            synchronized(inputLock) { duplex = session }
            duplexJob?.start()
            synchronized(inputLock) { ensureUplinkDrain() }
        }

        private fun acceptCleanAudio(bytes: ByteArray) {
            if (!started) return
            // 静音只阻止识别，不停止 AEC 学习；始终允许用户在播报期间讲话并打断。
            val muted = state.value.muted || state.value.inputPaused
            val now = System.nanoTime()
            if (now - lastInputLevelNanos >= 50_000_000L) {
                lastInputLevelNanos = now
                mutableState.update { it.copy(inputLevel = if (muted) 0f else calculatePcm16Level(bytes)) }
            }
            if (!muted) {
                if (duplexMode) {
                    detector?.accept(bytes) { frame, probability ->
                        val gated = gatedProbability(frame, probability)
                        uplinkFrame(frame, gated)
                        segmenter.accept(frame, gated)
                    }
                } else {
                    detector?.accept(bytes) { frame, probability ->
                        // 底噪估计必须用门控前的原始概率与「未在播放」状态：门控把播放期
                        // 帧压到 0,会把回声伪装成静默污染估计。
                        val level = calculatePcm16Level(frame)
                        noiseFloorTracker.accept(
                            level,
                            probability,
                            contaminated = player.playing || echoGuard.tailRisk(),
                        )
                        segmenter.accept(frame, gatedProbability(frame, probability, level))
                    }
                }
            }
        }

        /**
         * 常驻回声门控：播放期与播完后的声学尾音期都把回声主导的帧概率压到 0，
         * 但仍喂给切句器与上行预滚环，前置音频和 VAD 隐藏状态都不丢，只是不会
         * 触发新一轮用户输入；明显强于回声的近端人声照常放行打断。两种模式共用。
         */
        private fun gatedProbability(frame: ByteArray, probability: Float, level: Float = calculatePcm16Level(frame)): Float = echoGuard.gate(
            isPlaying = player.playing,
            speechProbability = probability,
            microphoneLevel = level,
            referenceLevel = currentReferenceLevel,
        )

        /** 硬件 AEC 路径：按录音块时长采样播放参考，取最强的一段代表本块回声电平。 */
        private fun playbackReferenceLevel(pcm16: ByteArray, firstTime: Long): Float {
            val durationNanos = pcm16.size / 2 * 1_000_000_000L / VoiceAudioFormat.INPUT_SAMPLE_RATE_HZ
            var level = 0f
            var offset = 0L
            var probes = 0
            while (offset < durationNanos && probes++ < MAX_REFERENCE_PROBES) {
                level = maxOf(level, calculatePcm16Level(player.echoReference(firstTime + offset)))
                offset += REFERENCE_PROBE_NANOS
            }
            return level
        }

        /**
         * Realtime 模式的本地上行门控：嘈杂环境的人群声对模型同样是语音，
         * 统一由本地 VAD 把关，静默帧不上行，服务端只收到判定为语音的帧（含预滚
         * 前导音）。服务端自动判轮关闭，轮次边界由同一上行队列中的提交控制显式触发；播放期
         * 与播完尾音期回声主导的帧已被 [PlaybackEchoGuard] 压到 0，天然进不了上行。
         */
        private fun uplinkFrame(frame: ByteArray, probability: Float) {
            if (!uplinkGateOpen) {
                if (probability < UPLINK_GATE_THRESHOLD) {
                    // VAD 的 frame 按帧复用，预滚环跨帧持有必须拷贝。
                    uplinkPreRoll.addLast(frame.copyOf())
                    while (uplinkPreRoll.size > UPLINK_PRE_ROLL_FRAMES) uplinkPreRoll.removeFirst()
                    return
                }
                uplinkGateOpen = true
                for (pending in uplinkPreRoll) sendUplink(pending)
                uplinkPreRoll.clear()
            }
            if (probability >= UPLINK_GATE_THRESHOLD) sendUplink(frame) else uplinkGateOpen = false
        }

        private fun sendUplink(frame: ByteArray) {
            // 入队即拷贝：VAD 的逐帧缓冲复用，且发送是异步的，队列必须持有独立副本。
            // 会话未就绪时只累积（队列有界）；就绪后由单一 drain 协程按序发送。
            synchronized(inputLock) {
                uplinkQueue.enqueue(frame)
                ensureUplinkDrain()
            }
        }

        /**
         * 拉起唯一的上行消费协程。调用方必须持有 inputLock：与 [duplex] 的交换、入队和
         * 「队列已空」的收尾在同一把锁内闭合，不会出现帧滞留或重复消费。
         */
        private fun ensureUplinkDrain() {
            if (uplinkDrainJob?.isActive == true) return
            val session = duplex ?: return
            // 即使从主线程入队也先登记 Job，避免同步失败时清理遗漏当前消费者。
            uplinkDrainJob = scope.launch(Dispatchers.Main) {
                while (isActive) {
                    val item = synchronized(inputLock) {
                        uplinkQueue.poll() ?: run {
                            // 队列排空后释放 job；此后入队会重新拉起消费协程。
                            uplinkDrainJob = null
                            null
                        }
                    } ?: break
                    cancellationAwareRunCatching {
                        when (item) {
                            is UplinkFrameQueue.Item.Audio -> session.sendAudio(item.pcm16)
                            UplinkFrameQueue.Item.CommitTurn -> session.commitTurn()
                            UplinkFrameQueue.Item.Interrupt -> session.interrupt()
                        }
                    }.onFailure { error ->
                        if (!closed) {
                            android.util.Log.w(TAG, "Realtime 上行命令处理失败", error)
                            fail(RealtimeVoiceFailure.Generation)
                        }
                    }
                }
            }
        }

        private suspend fun speechStarted() {
            if (closed || !started || state.value.muted || state.value.inputPaused) return
            recognitionJob?.cancel()
            replyActive = false
            player.stop()
            // 人声开始即取消整条生成/合成链路，不能等待下一次识别才停止付费请求。
            replyJob?.cancel()
            if (duplexMode) {
                // 本地即时停播，并显式取消服务端活动响应；后续 response.done 负责会话收尾。
                duplexPlayback?.cancel()
                duplexPlayback = null
                cancellationAwareRunCatching { duplex?.interrupt() }.onFailure {
                    android.util.Log.w(TAG, "Realtime 本地打断失败", it)
                }
            }
            mutableState.update { it.copy(phase = RealtimeVoicePhase.Listening, speakingText = "", outputLevel = 0f) }
        }

        // —— Realtime 模式:服务端事件驱动的状态机与播放 ——

        /**
         * 进入 Thinking 后挂起的兜底计时。服务端未返回 response.done、工具二轮没触发、
         * 或工具轮异常漏发 response.create 时，[finishDuplexPlayback] 会因 toolNotice
         * 刻意保持 Thinking，且没有任何后续事件能把它拉出来。超时只在该状态仍为
         * Thinking 时回 Listening，不打断正在播报的 Speaking，也不覆盖失败态。
         */
        private fun armResponseWatchdog() {
            responseWatchdog?.cancel()
            responseWatchdog = scope.launch {
                delay(RESPONSE_WATCHDOG_MS)
                if (closed || !started) return@launch
                android.util.Log.w(TAG, "Realtime 响应超时，退出等待状态")
                mutableState.update { current ->
                    if (current.phase == RealtimeVoicePhase.Thinking) {
                        current.copy(
                            phase = RealtimeVoicePhase.Listening,
                            toolNotice = null,
                            speakingText = "",
                            outputLevel = 0f,
                        )
                    } else {
                        current
                    }
                }
            }
        }

        private suspend fun handleDuplexEvent(event: RealtimeVoiceEvent) {
            when (event) {
                is RealtimeVoiceEvent.Ready -> if (event.reconnected) {
                    // 重连即新会话:清空字幕上下文,提示位由界面渲染「已重新连接」。
                    mutableState.update { it.copy(reconnectNotice = true, responseText = "", speakingText = "", toolNotice = null) }
                }
                is RealtimeVoiceEvent.UserTranscript -> mutableState.update { it.copy(inputText = event.text, inputAttachments = emptyList()) }
                RealtimeVoiceEvent.TurnCommitted -> {
                    val turn = state.value.revision + 1
                    mutableState.update { it.copy(
                        phase = RealtimeVoicePhase.Thinking, revision = turn, failure = null,
                        responseText = "", spokenText = "", speakingText = "", reconnectNotice = false,
                        toolNotice = null,
                    ) }
                    armResponseWatchdog()
                }
                is RealtimeVoiceEvent.AssistantTranscriptDelta -> mutableState.update {
                    if (it.phase == RealtimeVoicePhase.Thinking || it.phase == RealtimeVoicePhase.Speaking) {
                        // 第二轮（工具结果后）的转写增量继续拼接，字幕保持 announcement + answer 连续。
                        it.copy(responseText = it.responseText + event.text, toolNotice = null)
                    } else {
                        it
                    }
                }
                is RealtimeVoiceEvent.ToolCallStarted -> {
                    // 工具调用不进语音播报：保持 Thinking 等二轮推理，界面用 toolNotice 提示；
                    // 重新计时，因为二轮推理此刻才开始。
                    mutableState.update { it.copy(phase = RealtimeVoicePhase.Thinking, toolNotice = event.toolName) }
                    armResponseWatchdog()
                }
                is RealtimeVoiceEvent.AssistantAudio -> onAssistantAudio(event.pcm24k)
                RealtimeVoiceEvent.Interrupted -> stopDuplexPlayback()
                RealtimeVoiceEvent.TurnCompleted -> finishDuplexPlayback()
                is RealtimeVoiceEvent.Failed -> fail(event.failure)
            }
        }

        private fun onAssistantAudio(bytes: ByteArray) {
            // 音频恢复即视为二轮推理已开始播报，工具提示随之退场。
            mutableState.update { if (it.toolNotice != null) it.copy(toolNotice = null) else it }
            val channel = duplexPlayback ?: Channel<ByteArray>(Channel.UNLIMITED).also { created ->
                duplexPlayback = created
                duplexPlaybackJob = scope.launch { playDuplex(created.receiveAsFlow()) }
            }
            channel.trySend(bytes)
        }

        private suspend fun playDuplex(source: Flow<ByteArray>) {
            cancellationAwareRunCatching {
                withContext(Dispatchers.Default) {
                    player.play(source, player.checkpoint(), onLevel = { level ->
                        mutableState.update { it.copy(outputLevel = level) }
                    }) {
                        mutableState.update { it.copy(phase = RealtimeVoicePhase.Speaking) }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                android.util.Log.w(TAG, "Realtime 播放失败", error)
                fail(RealtimeVoiceFailure.Playback)
            }
        }

        private suspend fun stopDuplexPlayback() {
            duplexPlayback?.cancel()
            duplexPlayback = null
            player.stop()
            mutableState.update { it.copy(phase = RealtimeVoicePhase.Listening, speakingText = "", outputLevel = 0f, toolNotice = null) }
        }

        private suspend fun finishDuplexPlayback() {
            val channel = duplexPlayback
            val spoken = state.value.responseText
            duplexPlayback = null
            if (channel != null) {
                channel.close()
                // 服务端响应已结束;等待播放器排空剩余音频后再回监听。
                duplexPlaybackJob?.join()
            }
            duplexPlaybackJob = null
            mutableState.update {
                // 工具二轮推理尚未产出（toolNotice 未清）：保持 Thinking，让字幕增量的
                // Thinking/Speaking 守卫接住第二轮；二轮结束（通知已清）回到监听。
                // 这个「保持」不是无限期：响应看门狗超时会把 Thinking 拉回 Listening。
                if (it.toolNotice != null) it else it.copy(
                    phase = RealtimeVoicePhase.Listening, speakingText = "", outputLevel = 0f, spokenText = spoken,
                )
            }
            if (state.value.phase == RealtimeVoicePhase.Thinking) armResponseWatchdog()
            else responseWatchdog?.cancel()
        }

        private fun handleSemiCascadedUtterance(segment: UtteranceSegment) {
            if (closed || !started || state.value.muted || state.value.inputPaused) return
            if (!commitSegment(segment)) return
            val bytes = segment.pcm16
            val revision = inputRevision
            recognitionJob?.cancel()
            recognitionJob = scope.launch {
                cancellationAwareRunCatching {
                    val wavBytes = encodePcm16Wav(bytes, sampleRateHz = 16_000, channelCount = 1)
                    val attachment = FileAttachment(
                        mimeType = "audio/wav",
                        id = UUID.randomUUID().toString(),
                        sizeBytes = wavBytes.size.toLong(),
                        displayName = "voice.wav",
                        inlineData = wavBytes,
                    )
                    replyJob?.cancelAndJoin()
                    if (revision != inputRevision || closed || !started) return@cancellationAwareRunCatching
                    val previousSpokenText = state.value.spokenText
                    val turn = state.value.revision + 1
                    mutableState.update {
                        it.copy(
                            inputText = "",
                            inputAttachments = listOf(attachment),
                            responseText = "",
                            spokenText = "",
                            speakingText = "",
                            phase = RealtimeVoicePhase.Thinking,
                            revision = turn,
                            failure = null,
                        )
                    }
                    replyJob = scope.launch {
                        reply(
                            text = "",
                            revision = turn,
                            previousSpokenText = previousSpokenText,
                            attachments = listOf(attachment),
                        )
                    }
                }.onFailure {
                    if (revision == inputRevision) {
                        android.util.Log.w(TAG, "Semi-cascaded utterance handling failed", it)
                        fail(RealtimeVoiceFailure.AudioProcessing)
                    }
                }
            }
        }

        /**
         * 识别提交门:不达标的段直接回监听,不进 Transcribing、不花识别成本。
         * 丢弃与空识别同语义(无 generation、无 context slot);记录画像便于真机标定。
         */
        private fun commitSegment(segment: UtteranceSegment): Boolean {
            val floor = noiseFloorTracker.floor()
            val allowed = UtteranceCommitGate.allows(segment, floor)
            if (!allowed) {
                android.util.Log.d(
                    TAG,
                    "Utterance rejected by commit gate: peak=${segment.peakProbability} " +
                        "speech=${segment.speechFrameCount} voiced=${segment.voicedFrameCount} " +
                        "frames=${segment.frameCount} level=${segment.peakLevel} floor=$floor",
                )
            }
            return allowed
        }

        private fun recognize(segment: UtteranceSegment) {
            if (closed || !started || state.value.muted || state.value.inputPaused) return
            if (!commitSegment(segment)) return
            val bytes = segment.pcm16
            val revision = inputRevision
            recognitionJob?.cancel()
            recognitionJob = scope.launch {
                mutableState.update { it.copy(phase = RealtimeVoicePhase.Transcribing) }
                cancellationAwareRunCatching {
                    val text = withContext(Dispatchers.IO) {
                        var last = ""
                        session.transcribe(bytes).collect { event ->
                            when (event) {
                                // 中间结果只用于更新显示：用户能实时看到识别进度，但只有最终结果才是输入。
                                is RecognitionEvent.Partial -> if (revision == inputRevision && !closed && started) {
                                    mutableState.update { it.copy(inputText = event.text, inputAttachments = emptyList()) }
                                }
                                is RecognitionEvent.Final -> last = event.text
                            }
                        }
                        last.trim()
                    }
                    if (revision != inputRevision || closed || !started) return@cancellationAwareRunCatching
                    if (text.isBlank()) {
                        mutableState.update { it.copy(phase = RealtimeVoicePhase.Listening) }
                        return@cancellationAwareRunCatching
                    }
                    // 等待旧轮取消及文字提交完成，再让下一轮取得后端所有权。
                    replyJob?.cancelAndJoin()
                    if (revision != inputRevision || closed || !started) return@cancellationAwareRunCatching
                    val previousSpokenText = state.value.spokenText
                    val turn = state.value.revision + 1
                    mutableState.update { it.copy(inputText = text, inputAttachments = emptyList(), responseText = "", spokenText = "", speakingText = "",
                        phase = RealtimeVoicePhase.Thinking, revision = turn, failure = null) }
                    replyJob = scope.launch { reply(text, turn, previousSpokenText) }
                }.onFailure { if (revision == inputRevision) {
                    android.util.Log.w(TAG, "Voice recognition failed")
                    fail(RealtimeVoiceFailure.Recognition)
                } }
            }
        }

        private suspend fun reply(text: String, revision: Long, previousSpokenText: String, attachments: List<FileAttachment> = emptyList()) {
            val playbackToken = player.checkpoint()
            replyActive = true
            try {
                cancellationAwareRunCatching {
                    coroutineScope {
                        val segments = Channel<VoiceAudioEvent.Audio>(1)
                        val playback = launch {
                            for (segment in segments) playSegment(segment, revision, playbackToken)
                        }
                        try {
                            session.respond(text, previousSpokenText, attachments).collect { event ->
                                when (event) {
                                    is VoiceAudioEvent.Text -> mutableState.update {
                                        if (it.revision == revision) it.copy(responseText = it.responseText + event.text) else it
                                    }
                                    is VoiceAudioEvent.Audio -> segments.send(event)
                                    VoiceAudioEvent.Interrupted -> {
                                        player.stop()
                                        playback.cancel()
                                        segments.cancel()
                                        mutableState.update {
                                            if (it.revision == revision) it.copy(phase = RealtimeVoicePhase.Listening,
                                                speakingText = "", outputLevel = 0f) else it
                                        }
                                    }
                                }
                            }
                        } finally {
                            segments.close()
                        }
                        playback.join()
                        mutableState.update { if (it.revision == revision) it.copy(phase = RealtimeVoicePhase.Listening) else it }
                    }
                }.onFailure { error ->
                    if (!closed && state.value.revision == revision) {
                        android.util.Log.w(TAG, "Voice reply failed", error)
                        val failure = (error as? VoiceAudioBackendException)?.failure
                            ?: if (error is VoiceException) RealtimeVoiceFailure.Synthesis else RealtimeVoiceFailure.Generation
                        fail(failure)
                    }
                }
            } finally {
                replyActive = false
                // 用户挂断的当前轮交给 session.close()，由它提交生成文字并释放租约。
                if (currentCoroutineContext()[Job] !== replyToFinalizeOnClose) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        cancellationAwareRunCatching { session.finishTurn() }.onFailure {
                            withContext(Dispatchers.Main.immediate) { fail(RealtimeVoiceFailure.Persistence) }
                        }
                    }
                }
            }
        }

        private suspend fun playSegment(segment: VoiceAudioEvent.Audio, revision: Long, playbackToken: Long) = coroutineScope {
            val began = MutableStateFlow(false)
            val caption = launch {
                combine(segment.text, began) { text, playing -> text to playing }.collect { (text, playing) ->
                    if (playing) mutableState.update { if (replyActive && it.revision == revision) it.copy(speakingText = text) else it }
                }
            }
            try {
                cancellationAwareRunCatching {
                    // 播放循环（AudioTrack 写入、能量计算、轮询等待）移出主线程，
                    // 避免与同一主线程上的状态更新、路由回调争抢造成实时录音掉帧；
                    // 回调里的状态回写经 StateFlow.update，本身线程安全
                    withContext(Dispatchers.Default) {
                        player.play(segment.audio, playbackToken, onLevel = { level ->
                            mutableState.update { if (replyActive && it.revision == revision) it.copy(outputLevel = level) else it }
                        }) {
                            began.value = true
                            mutableState.update { if (replyActive && it.revision == revision) it.copy(
                                phase = RealtimeVoicePhase.Speaking, speakingText = segment.text.value) else it }
                        }
                    }
                }.getOrElse { error ->
                    throw VoiceAudioBackendException(
                        (error as? VoiceAudioBackendException)?.failure ?: RealtimeVoiceFailure.Playback,
                        cause = error,
                    )
                }
                session.confirmPlayback(segment.id, segment.text.value)
                mutableState.update { if (replyActive && it.revision == revision) it.copy(
                    spokenText = it.spokenText + segment.text.value, speakingText = "", phase = RealtimeVoicePhase.Thinking) else it }
            } finally { caption.cancel() }
        }

        override fun setMuted(muted: Boolean) {
            scope.launch {
                if (closed) return@launch
                inputRevision++
                recognitionJob?.cancel()
                mutableState.update { it.copy(muted = muted, listening = started && !muted && !it.inputPaused, inputLevel = 0f) }
                withContext(Dispatchers.IO) {
                    synchronized(inputLock) {
                        detector?.reset()
                        segmenter.reset()
                        noiseFloorTracker.reset()
                    }
                }
            }
        }

        override fun setInputPaused(paused: Boolean) {
            if (closed) return
            inputRevision++
            recognitionJob?.cancel()
            mutableState.update { it.copy(inputPaused = paused, listening = started && !paused && !it.muted, inputLevel = 0f) }
            uplinkDrainJob?.cancel()
            uplinkDrainJob = null
            scope.launch(Dispatchers.IO) {
                synchronized(inputLock) {
                    detector?.reset()
                    segmenter.reset()
                    noiseFloorTracker.reset()
                    uplinkPreRoll.clear()
                    uplinkQueue.clear()
                    uplinkGateOpen = false
                }
            }
        }

        override suspend fun submit(input: VoiceManualInput): VoiceInputResult = withContext(Dispatchers.Main.immediate) {
            if (closed || !started || state.value.failure != null || (input.text.isBlank() && input.attachments.isEmpty())) {
                return@withContext VoiceInputResult.Unavailable
            }
            try {
                // 读取媒体和协议准入在停播前完成；重连中不缓存手动消息，等待用户重试。
                val prepared = if (duplexMode) {
                    duplex?.prepareInput(input) ?: return@withContext VoiceInputResult.Unavailable
                } else input
                if (closed || !started) return@withContext VoiceInputResult.Unavailable
                inputRevision++
                recognitionJob?.cancelAndJoin()
                val spoken = state.value.spokenText
                replyActive = false
                player.stop()
                replyJob?.cancelAndJoin()
                if (closed || !started) return@withContext VoiceInputResult.Unavailable
                if (duplexMode) {
                    duplexPlayback?.cancel()
                    duplexPlayback = null
                    duplexPlaybackJob?.cancelAndJoin()
                    duplexPlaybackJob = null
                    duplex?.submit(prepared) ?: return@withContext VoiceInputResult.Unavailable
                }
                val turn = state.value.revision + 1
                mutableState.update { it.copy(
                    inputText = input.text, inputAttachments = input.attachments,
                    responseText = "", spokenText = "", speakingText = "", outputLevel = 0f,
                    phase = RealtimeVoicePhase.Thinking, revision = turn, toolNotice = null,
                ) }
                if (duplexMode) armResponseWatchdog()
                else replyJob = scope.launch { reply(input.text, turn, spoken, input.attachments) }
                VoiceInputResult.Accepted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: UnsupportedOperationException) {
                VoiceInputResult.Unsupported
            } catch (_: IllegalStateException) {
                VoiceInputResult.Unavailable
            } catch (failure: Exception) {
                // 兜底吞异常必须留痕：手动提交失败只有 Failed 状态，没有日志无法定位根因。
                android.util.Log.w(TAG, "Manual input submit failed", failure)
                VoiceInputResult.Failed
            }
        }

        private fun fail(failure: RealtimeVoiceFailure) {
            if (closed) return
            started = false
            inputRevision++
            replyActive = false
            player.stop()
            replyJob?.cancel()
            if (duplexMode) {
                duplexPlayback?.cancel()
                duplexPlayback = null
                val eventJob = duplexJob
                duplexJob = null
                // 失败即停掉上行消费并弃置暂存帧：跨入下一次通话的陈旧音频不应随重试冲入新会话。
                // 置空 duplex 与清空队列同锁完成，避免录音线程在两者之间把帧重新攒进队列。
                val (duplexSession, uplinkJob) = synchronized(inputLock) {
                    val active = duplex to uplinkDrainJob
                    duplex = null
                    uplinkDrainJob = null
                    uplinkQueue.clear()
                    active
                }
                lifecycle.closeFailedDuplex(scope, duplexSession, eventJob, uplinkJob)
            }
            recorder.stop()
            restoreAudioRouting()
            mutableState.update { it.copy(phase = RealtimeVoicePhase.Failed, listening = false, failure = failure,
                speakingText = "", inputLevel = 0f, outputLevel = 0f) }
        }

        override suspend fun retry() {
            if (closed || state.value.phase != RealtimeVoicePhase.Failed) return
            if (!duplexMode && state.value.failure == RealtimeVoiceFailure.Persistence) {
                val saved = cancellationAwareRunCatching { session.retryPersistence() }.isSuccess
                if (!saved) return
            }
            replyJob?.join()
            recorder.stopAndJoin()
            if (duplexMode) {
                lifecycle.awaitFailedDuplexCleanup()
                duplexPlayback?.cancel()
                duplexPlayback = null
                duplexPlaybackJob = null
            }
            withContext(Dispatchers.IO) {
                synchronized(inputLock) {
                    detector?.close()
                    detector = null
                    echoCanceller?.close()
                    echoCanceller = null
                    segmenter.reset()
                    noiseFloorTracker.reset()
                }
            }
            if (!closed) start()
        }

        override suspend fun finishPendingTurn(): Boolean = withContext(NonCancellable + Dispatchers.Main.immediate) {
            lifecycle.finishPendingTurn {
                inputRevision++
                started = false
                replyActive = false
                player.stop()
                recognitionJob?.cancel()
                replyJob?.cancel()
                recorder.stopAndJoin()
                recognitionJob?.join()
                replyJob?.join()
                restoreAudioRouting()
                mutableState.update { it.copy(listening = false, speakingText = "", inputLevel = 0f, outputLevel = 0f) }
                if (duplexMode) {
                    // Realtime 模式无持久化提交;只需停播与清理播放段。
                    duplexPlayback?.cancel()
                    duplexPlayback = null
                    player.stop()
                    true
                } else {
                    val saved = cancellationAwareRunCatching { withContext(Dispatchers.IO) { session.finishTurn() } }.isSuccess
                    if (!saved) fail(RealtimeVoiceFailure.Persistence)
                    saved
                }
            }
        }

        override suspend fun close() = withContext(NonCancellable + Dispatchers.Main.immediate) {
            // 页面挂断与状态栏结束可能并发到达；后来的调用也必须等到旧资源真正释放。
            lifecycle.close {
                // 已被打断并进入收尾的轮次仍按通话内规则提交。
                replyToFinalizeOnClose = replyJob?.takeIf { it.isActive }
                replyActive = false
                inputRevision++
                player.stop()
                recorder.releaseAndJoin()
                recognitionJob?.cancel()
                replyJob?.cancelAndJoin()
                if (duplexMode) {
                    duplexPlayback?.cancel()
                    duplexPlayback = null
                    duplexJob?.cancelAndJoin()
                    duplexJob = null
                    uplinkDrainJob?.cancel()
                    uplinkDrainJob = null
                    synchronized(inputLock) { uplinkQueue.clear() }
                    val duplexSession = duplex
                    duplex = null
                    withContext(Dispatchers.IO) { runCatching { duplexSession?.close() } }
                }
                withContext(Dispatchers.IO) {
                    synchronized(inputLock) {
                        detector?.close(); detector = null
                        echoCanceller?.close(); echoCanceller = null
                        segmenter.reset()
                        noiseFloorTracker.reset()
                    }
                }
                try {
                    if (!duplexMode) {
                        try {
                            // 退出仍尝试提交；存储失败不能阻止麦克风、路由及会话租约的释放。
                            withContext(Dispatchers.IO) { session.close() }
                        } catch (_: VoiceAudioBackendException) {
                            mutableState.update { it.copy(failure = RealtimeVoiceFailure.Persistence) }
                        }
                    }
                } finally {
                    restoreAudioRouting()
                    mutableState.update { it.copy(phase = RealtimeVoicePhase.Closed, listening = false, inputLevel = 0f, outputLevel = 0f) }
                    scope.cancel()
                }
            }
        }

        private fun restoreAudioRouting() {
            audio.abandonAudioFocusRequest(focus)
            if (routingOwned) {
                routing.close()
                audio.mode = previousMode
                routingOwned = false
            }
        }
    }

    private companion object {
        private const val TAG = "RealtimeVoiceSession"

        /**
         * Thinking 的兜底等待上限：覆盖首包生成与一轮工具执行 + 二轮推理的正常耗时，
         * 超时只回 Listening，不改对话历史。避免服务端不收尾时界面永久显示「正在生成」。
         */
        private const val RESPONSE_WATCHDOG_MS = 30_000L

        /**
         * Realtime 模式全程上行门控:本地 VAD 概率达到该值的帧才放行。
         * 与切句器起始阈值对齐,静默帧不上行,噪声不进服务端判轮。
         */
        private const val UPLINK_GATE_THRESHOLD = 0.75f

        /** 上行门控的预滚帧数(32ms/帧);与设计 D4 的 12 帧预滚一致。 */
        private const val UPLINK_PRE_ROLL_FRAMES = 12

        /**
         * 会话就绪前的上行暂存上限(32ms/帧,约 4 秒 16 kHz);与会话层 pendingAudio 的
         * 256 KiB 容量同量级,超出丢最旧——更早的音频由下游缓冲能力兜底,不再放大暂存。
         * 每帧入队即拷贝(VAD 帧缓冲复用),故实际占用为帧数 × 单帧字节数。
         */
        private const val PENDING_UPLINK_FRAMES = 128

        /** 硬件 AEC 路径下，单个录音块最多查询多少个 10 ms 播放参考片段。 */
        private const val MAX_REFERENCE_PROBES = 8
        private const val REFERENCE_PROBE_NANOS = 10_000_000L
    }
}
