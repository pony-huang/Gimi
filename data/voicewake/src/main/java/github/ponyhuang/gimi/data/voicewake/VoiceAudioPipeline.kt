package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.audio.CaptureDecision
import github.ponyhuang.gimi.core.audio.PcmPreRollBuffer
import github.ponyhuang.gimi.core.audio.VoiceCommandCapture
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.isVoiceConfirmationApproved
import github.ponyhuang.gimi.domain.speech.model.stripWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.voiceConfirmationTarget
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 可由 Android Service 投射到通知的语音运行时状态。
 *
 * @property status 当前语音唤醒状态。
 * @property message 面向用户展示的本地化状态文本。
 * @property deviceName 当前音频路由名称。
 * @property lastCommand 最近一次识别并提交的语音命令。
 */
data class VoicePipelineEvent(
    val status: BluetoothVoiceStatus,
    val message: String,
    val deviceName: String? = null,
    val lastCommand: String? = null,
)

/**
 * 蓝牙语音完整运行时协调器。
 *
 * Service 只转发生命周期、权限和通话状态；本类拥有音频路由、录音、唤醒检测、ASR、
 * Agent、确认、TTS、错误恢复以及这些资源的取消和释放时序。
 */
@Singleton
class VoiceAudioPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: BluetoothVoiceController,
    private val audioRouter: BluetoothAudioRouter,
    private val speechRecognition: SpeechRecognitionRepository,
    private val agentTasks: VoiceAgentTaskExecutor,
    private val speechPlayer: VoiceSpeechPlayer,
    private val wakeModels: WakeModelProvider,
) {
    private val audioLock = Any()
    private var runtimeScope: CoroutineScope? = null
    private var route: VoiceAudioRoute? = null
    private var recorder: BluetoothPcmRecorder? = null
    private var detector: VoskWakeWordDetector? = null
    private var preRoll = PcmPreRollBuffer()
    private var capture: VoiceCommandCapture? = null
    private var processingJob: Job? = null
    private var recoveryJob: Job? = null
    private var recoveryAttempts = 0
    private var pausedByUser = false
    private var pausedByCall = false
    private var lastWakeAtMs = 0L
    private var cueActiveUntilMs = 0L
    private var cueToneGenerator: ToneGenerator? = null

    private val _events = MutableSharedFlow<VoicePipelineEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<VoicePipelineEvent> = _events.asSharedFlow()

    /** 启动或重新启动完整语音运行时。 */
    fun start() {
        ensureScope()
        recoveryJob?.cancel()
        recoveryAttempts = 0
        pausedByUser = false
        pausedByCall = false
        if (cueToneGenerator == null) {
            cueToneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, TONE_VOLUME_PERCENT)
            }.getOrNull()
        }
        audioRouter.observe { launchRuntime { reconcileBluetoothRoute() } }
        launchRuntime { reconcileBluetoothRoute() }
    }

    /** 用户主动暂停监听，同时取消正在执行的语音任务。 */
    fun pause() {
        pausedByUser = true
        recoveryJob?.cancel()
        agentTasks.stop()
        processingJob?.cancel()
        processingJob = null
        stopAudioCapture(releaseRoute = true)
        setStatus(BluetoothVoiceStatus.Paused, context.getString(R.string.bluetooth_voice_status_paused))
    }

    /** 用户恢复监听。 */
    fun resume() {
        pausedByUser = false
        if (!pausedByCall) launchRuntime { reconcileBluetoothRoute() }
    }

    /** 通话占用音频时暂停，但保留用户暂停状态。 */
    fun pauseForCall() {
        if (pausedByCall) return
        pausedByCall = true
        stopAudioCapture(releaseRoute = true)
        setStatus(BluetoothVoiceStatus.Paused, context.getString(R.string.bluetooth_voice_status_call_paused))
    }

    /** 通话结束后按用户暂停状态决定是否恢复。 */
    fun resumeAfterCall() {
        if (!pausedByCall) return
        pausedByCall = false
        if (!pausedByUser) launchRuntime { reconcileBluetoothRoute() }
    }

    /** 停止运行时并释放本次 Service 生命周期持有的全部资源。 */
    fun stop() {
        pausedByUser = true
        recoveryJob?.cancel()
        recoveryJob = null
        processingJob?.cancel()
        processingJob = null
        agentTasks.stop()
        stopAudioCapture(releaseRoute = true)
        wakeModels.release()
        cueToneGenerator?.release()
        cueToneGenerator = null
        audioRouter.stopObserving()
        runtimeScope?.cancel()
        runtimeScope = null
        setStatus(BluetoothVoiceStatus.Stopped, message = "", deviceName = null, lastCommand = null)
    }

    private fun ensureScope(): CoroutineScope = runtimeScope ?: CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    ).also { runtimeScope = it }

    private fun launchRuntime(block: suspend CoroutineScope.() -> Unit): Job =
        ensureScope().launch(block = block)

    private suspend fun reconcileBluetoothRoute() {
        if (pausedByUser || pausedByCall || processingJob?.isActive == true) return
        val available = runCatching {
            audioRouter.findRoute(controller.state.value.bluetoothOnly)
        }.getOrNull()
        if (available == null) {
            stopAudioCapture(releaseRoute = true)
            setStatus(
                BluetoothVoiceStatus.WaitingForBluetooth,
                context.getString(R.string.bluetooth_voice_status_waiting_bluetooth),
            )
            return
        }
        val current = route
        if (current?.id == available.id && recorder != null &&
            controller.state.value.status == BluetoothVoiceStatus.Listening
        ) return
        startListening(available)
    }

    private suspend fun startListening(newRoute: VoiceAudioRoute) {
        stopAudioCapture(releaseRoute = false)
        val modelPath = controller.modelPath()
        if (modelPath == null) {
            setStatus(BluetoothVoiceStatus.Error, context.getString(R.string.bluetooth_voice_status_model_missing))
            return
        }
        val activated = runCatching { audioRouter.activate(newRoute) }.getOrDefault(false)
        if (!activated) {
            audioRouter.release()
            setStatus(
                BluetoothVoiceStatus.WaitingForBluetooth,
                context.getString(R.string.bluetooth_voice_status_bluetooth_audio_unavailable),
            )
            return
        }
        route = newRoute
        val wakeDetector = cancellationAwareRunCatching {
            withContext(Dispatchers.IO) {
                val model = withTimeout(ACQUIRE_MODEL_TIMEOUT_MS) { wakeModels.acquire(modelPath) }
                VoskWakeWordDetector(model, controller.state.value.activeModel.wakeWordGrammar)
            }
        }.getOrElse { error ->
            audioRouter.release()
            route = null
            setStatus(
                BluetoothVoiceStatus.Error,
                error.message ?: context.getString(R.string.bluetooth_voice_status_model_load_failed),
            )
            return
        }
        // 模型加载可跨越通话状态变化；拿到模型后必须重新检查，不能在暂停后启动麦克风。
        if (pausedByUser || pausedByCall) {
            wakeDetector.close()
            audioRouter.release()
            route = null
            return
        }
        detector = wakeDetector
        preRoll = PcmPreRollBuffer()
        capture = null
        val pcmRecorder = BluetoothPcmRecorder()
        recorder = pcmRecorder
        val started = pcmRecorder.start(
            route = newRoute,
            onChunk = ::onAudioChunk,
            onError = { error -> launchRuntime { recoverFromAudioError(error) } },
        )
        if (!started) {
            recorder = null
            wakeDetector.close()
            detector = null
            audioRouter.release()
            route = null
            setStatus(BluetoothVoiceStatus.Error, context.getString(R.string.bluetooth_voice_status_mic_start_failed))
            return
        }
        recoveryAttempts = 0
        setStatus(
            BluetoothVoiceStatus.Listening,
            context.getString(R.string.bluetooth_voice_status_listening, controller.state.value.wakeWord),
            deviceName = newRoute.name,
        )
    }

    private fun onAudioChunk(chunk: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        synchronized(audioLock) {
            preRoll.append(chunk)
            val activeCapture = capture
            if (activeCapture != null) {
                // 提示音保护窗内的采样不能进入指令，避免提示音被 ASR 当成用户输入。
                if (now < cueActiveUntilMs) return
                when (val decision = activeCapture.append(chunk, now)) {
                    CaptureDecision.Continue -> Unit
                    CaptureDecision.Cancel -> {
                        capture = null
                        detector?.reset()
                        setStatus(
                            BluetoothVoiceStatus.Listening,
                            context.getString(R.string.bluetooth_voice_status_no_command),
                        )
                    }
                    is CaptureDecision.Complete -> {
                        capture = null
                        recorder?.stop()
                        recorder = null
                        processingJob = launchRuntime { processCommand(decision.pcm16) }
                    }
                }
                return
            }
            if (controller.state.value.status != BluetoothVoiceStatus.Listening ||
                now - lastWakeAtMs < WAKE_COOLDOWN_MS
            ) return
            val detected = runCatching { detector?.accept(chunk) == true }.getOrElse { error ->
                launchRuntime { recoverFromAudioError(error) }
                false
            }
            if (detected) {
                lastWakeAtMs = now
                cueActiveUntilMs = now + WAKE_CUE_GUARD_MS
                capture = VoiceCommandCapture(preRoll.snapshot(), now)
                launchRuntime { playWakeCue() }
                setStatus(
                    BluetoothVoiceStatus.CapturingCommand,
                    context.getString(R.string.bluetooth_voice_status_say_command),
                )
            }
        }
    }

    private suspend fun playWakeCue() {
        val tone = cueToneGenerator ?: return
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
            delay(TONE_DELAY_MS)
        } catch (_: RuntimeException) {
            cueToneGenerator = null
        }
    }

    private suspend fun processCommand(pcm16: ByteArray) {
        try {
            cancellationAwareRunCatching {
                setStatus(BluetoothVoiceStatus.Transcribing, context.getString(R.string.bluetooth_voice_status_transcribing))
                val transcript = speechRecognition.transcribe(pcm16)
                val activeModel = controller.state.value.activeModel
                val command = stripWakeKeyword(
                    stripWakeKeyword(transcript, activeModel.wakeWord),
                    activeModel.wakeWordGrammar,
                )
                check(command.isNotBlank()) { context.getString(R.string.bluetooth_voice_status_no_task_content) }
                setStatus(
                    BluetoothVoiceStatus.RunningAgent,
                    context.getString(
                        R.string.bluetooth_voice_status_running_agent,
                        command.take(NOTIFICATION_PREVIEW_LENGTH),
                    ),
                    lastCommand = command,
                )
                val result = agentTasks.execute(command, ::confirmVoiceTool)
                val activeRoute = runCatching {
                    audioRouter.findRoute(controller.state.value.bluetoothOnly)
                }.getOrNull()
                if (activeRoute != null && speechPlayer.isAvailable()) {
                    setStatus(
                        BluetoothVoiceStatus.Speaking,
                        context.getString(R.string.bluetooth_voice_status_speaking),
                        deviceName = activeRoute.name,
                        lastCommand = command,
                    )
                    speechPlayer.play(markdownToSpeechText(result.responseText), activeRoute)
                }
                setStatus(
                    BluetoothVoiceStatus.Listening,
                    result.responseText.take(NOTIFICATION_PREVIEW_LENGTH),
                    deviceName = activeRoute?.name,
                    lastCommand = command,
                )
            }.onFailure { error ->
                setStatus(
                    BluetoothVoiceStatus.Error,
                    error.message ?: context.getString(R.string.bluetooth_voice_status_task_failed),
                    lastCommand = controller.state.value.lastCommand,
                )
                delay(ERROR_DISPLAY_DELAY_MS)
            }
        } finally {
            processingJob = null
            if (!pausedByUser && !pausedByCall) reconcileBluetoothRoute()
        }
    }

    private suspend fun confirmVoiceTool(request: VoiceToolConfirmation): Boolean {
        val activeRoute = runCatching {
            audioRouter.findRoute(controller.state.value.bluetoothOnly)
        }.getOrNull() ?: return false
        if (!speechPlayer.isAvailable()) return false
        val wakeModel = activeWakeModel()
        setStatus(
            BluetoothVoiceStatus.Speaking,
            context.getString(R.string.bluetooth_voice_status_confirm_wait, request.toolName),
            deviceName = activeRoute.name,
        )
        val spokenTarget = voiceConfirmationTarget(request.arguments)
        val promptPlayed = speechPlayer.play(
            wakeModel.confirmationPromptTemplate.format(
                listOf(request.toolName, spokenTarget).filter(String::isNotBlank).joinToString("，"),
            ),
            activeRoute,
        )
        if (!promptPlayed) return false
        setStatus(
            BluetoothVoiceStatus.CapturingCommand,
            context.getString(R.string.bluetooth_voice_status_confirm_window, CONFIRMATION_TIMEOUT_MS / 1000),
            deviceName = activeRoute.name,
        )
        val audio = CompletableDeferred<ByteArray?>()
        val confirmationCapture = VoiceCommandCapture(
            preRoll = ByteArray(0),
            startedAtMs = SystemClock.elapsedRealtime(),
            speechStartTimeoutMs = CONFIRMATION_SPEECH_START_TIMEOUT_MS,
            maxCaptureMs = CONFIRMATION_TIMEOUT_MS,
        )
        val confirmationRecorder = BluetoothPcmRecorder()
        synchronized(audioLock) { recorder = confirmationRecorder }
        val started = confirmationRecorder.start(
            route = activeRoute,
            onChunk = { chunk ->
                when (val decision = confirmationCapture.append(chunk, SystemClock.elapsedRealtime())) {
                    CaptureDecision.Continue -> Unit
                    CaptureDecision.Cancel -> audio.complete(null)
                    is CaptureDecision.Complete -> audio.complete(decision.pcm16)
                }
            },
            onError = { audio.complete(null) },
        )
        if (!started) {
            synchronized(audioLock) { if (recorder === confirmationRecorder) recorder = null }
            confirmationRecorder.release()
            return false
        }
        val pcm16 = try {
            withTimeoutOrNull(CONFIRMATION_TIMEOUT_MS) { audio.await() }
        } finally {
            synchronized(audioLock) { if (recorder === confirmationRecorder) recorder = null }
            confirmationRecorder.release()
        } ?: return false
        setStatus(
            BluetoothVoiceStatus.Transcribing,
            context.getString(R.string.bluetooth_voice_status_transcribing_confirmation),
        )
        val transcript = runCatching { speechRecognition.transcribe(pcm16) }.getOrNull() ?: return false
        return isVoiceConfirmationApproved(transcript, wakeModel.confirmWords, wakeModel.rejectWords)
    }

    private fun activeWakeModel(): WakeModelInfo = controller.state.value.activeModel

    private suspend fun recoverFromAudioError(error: Throwable) {
        stopAudioCapture(releaseRoute = true)
        if (pausedByUser || pausedByCall) return
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            setStatus(BluetoothVoiceStatus.Error, recorderErrorMessage(error))
            return
        }
        recoveryAttempts += 1
        val delayMs = RECOVERY_BASE_DELAY_MS shl (recoveryAttempts - 1)
        setStatus(
            BluetoothVoiceStatus.Starting,
            context.getString(
                R.string.bluetooth_voice_status_retrying,
                delayMs / 1000,
                recoveryAttempts,
                MAX_RECOVERY_ATTEMPTS,
            ),
        )
        recoveryJob = launchRuntime {
            delay(delayMs)
            reconcileBluetoothRoute()
        }
    }

    private fun recorderErrorMessage(error: Throwable): String {
        val recorderError = error as? BluetoothRecorderException
            ?: return error.message ?: context.getString(R.string.bluetooth_voice_status_recorder_failed)
        return when (recorderError.reason) {
            BluetoothRecorderException.Reason.BufferSizeUnavailable ->
                context.getString(R.string.bluetooth_voice_error_buffer_size)
            BluetoothRecorderException.Reason.MicrophoneRouteFailed ->
                context.getString(R.string.bluetooth_voice_error_route_failed)
            BluetoothRecorderException.Reason.NotRecording ->
                context.getString(R.string.bluetooth_voice_error_not_recording)
            BluetoothRecorderException.Reason.ReadFailed ->
                context.getString(R.string.bluetooth_voice_error_read_failed, recorderError.errorCode ?: 0)
        }
    }

    private fun stopAudioCapture(releaseRoute: Boolean) {
        synchronized(audioLock) {
            recorder?.release()
            recorder = null
            detector?.close()
            detector = null
            capture = null
            preRoll = PcmPreRollBuffer()
        }
        if (releaseRoute) {
            audioRouter.release()
            route = null
        }
    }

    private fun setStatus(
        status: BluetoothVoiceStatus,
        message: String,
        deviceName: String? = controller.state.value.deviceName,
        lastCommand: String? = controller.state.value.lastCommand,
    ) {
        controller.setStatus(status, deviceName, lastCommand, message.takeIf(String::isNotEmpty))
        _events.tryEmit(VoicePipelineEvent(status, message, deviceName, lastCommand))
    }

    private companion object {
        const val WAKE_COOLDOWN_MS = 2_000L
        const val WAKE_CUE_GUARD_MS = 400L
        const val MAX_RECOVERY_ATTEMPTS = 3
        const val RECOVERY_BASE_DELAY_MS = 2_000L
        const val CONFIRMATION_TIMEOUT_MS = 15_000L
        const val CONFIRMATION_SPEECH_START_TIMEOUT_MS = 5_000L
        const val ACQUIRE_MODEL_TIMEOUT_MS = 30_000L
        const val NOTIFICATION_PREVIEW_LENGTH = 120
        const val TONE_VOLUME_PERCENT = 55
        const val TONE_DURATION_MS = 150
        const val TONE_DELAY_MS = 200L
        const val ERROR_DISPLAY_DELAY_MS = 3_000L
    }
}
