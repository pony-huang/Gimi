package github.ponyhuang.gimi.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.MainActivity
import github.ponyhuang.gimi.core.audio.CaptureDecision
import github.ponyhuang.gimi.core.audio.PcmPreRollBuffer
import github.ponyhuang.gimi.core.audio.VoiceCommandCapture
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.data.voicewake.BluetoothAudioRoute
import github.ponyhuang.gimi.data.voicewake.BluetoothAudioRouter
import github.ponyhuang.gimi.data.voicewake.BluetoothPcmRecorder
import github.ponyhuang.gimi.data.voicewake.BluetoothRecorderException
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceController
import github.ponyhuang.gimi.data.voicewake.BluetoothVoicePreferences
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceStatus
import github.ponyhuang.gimi.data.voicewake.VoskWakeWordDetector
import github.ponyhuang.gimi.data.voicewake.VoiceSpeechPlayer
import github.ponyhuang.gimi.data.voicewake.WakeModelProvider
import github.ponyhuang.gimi.domain.speech.model.isVoiceConfirmationApproved
import github.ponyhuang.gimi.domain.speech.model.stripWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.voiceConfirmationTarget
import github.ponyhuang.gimi.R
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class BluetoothVoiceService : Service() {
    @Inject lateinit var controller: BluetoothVoiceController
    @Inject lateinit var preferences: BluetoothVoicePreferences
    @Inject lateinit var audioRouter: BluetoothAudioRouter
    @Inject lateinit var speechRecognition: SpeechRecognitionRepository
    @Inject lateinit var agentTasks: VoiceAgentTaskExecutor
    @Inject lateinit var speechPlayer: VoiceSpeechPlayer
    @Inject lateinit var wakeModels: WakeModelProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioLock = Any()
    private var route: BluetoothAudioRoute? = null
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
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var cueToneGenerator: ToneGenerator? = null
    private val callModeListener = AudioManager.OnModeChangedListener { mode ->
        val callActive = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE
        scope.launch {
            if (callActive && !pausedByCall && (recorder != null || capture != null)) {
                pausedByCall = true
                stopAudioCapture(releaseRoute = true)
                setStatus(BluetoothVoiceStatus.Paused, "通话中，语音唤醒已暂停")
            } else if (!callActive && pausedByCall) {
                pausedByCall = false
                if (!pausedByUser) reconcileBluetoothRoute()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        cueToneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, TONE_VOLUME_PERCENT) }.getOrNull()
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(this), callModeListener)
        audioRouter.observe { scope.launch { reconcileBluetoothRoute() } }
        scope.launch {
            preferences.keyword.collect { keyword ->
                withContext(Dispatchers.IO) { detector?.updateKeyword(keyword) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_START) {
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_STOP -> stopCompletely()
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_PAUSE -> pauseListening()
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_RESUME -> {
                pausedByUser = false
                scope.launch { reconcileBluetoothRoute() }
            }
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_START -> startForegroundAndListen()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundAndListen() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(BluetoothVoiceStatus.Starting, getString(R.string.bluetooth_voice_status_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (!hasRequiredPermissions()) {
            setStatus(BluetoothVoiceStatus.Error, getString(R.string.bluetooth_voice_status_permission_missing))
            return
        }
        recoveryJob?.cancel()
        recoveryAttempts = 0
        pausedByUser = false
        scope.launch { reconcileBluetoothRoute() }
    }

    private suspend fun reconcileBluetoothRoute() {
        if (pausedByUser || processingJob?.isActive == true) return
        if (!hasRequiredPermissions()) {
            setStatus(BluetoothVoiceStatus.Error, getString(R.string.bluetooth_voice_status_permission_missing))
            return
        }
        val available = runCatching { audioRouter.findRoute() }.getOrNull()
        if (available == null) {
            stopAudioCapture(releaseRoute = true)
            setStatus(
                BluetoothVoiceStatus.WaitingForBluetooth,
                getString(R.string.bluetooth_voice_status_waiting_bluetooth),
            )
            return
        }
        val current = route
        if (current?.input?.id == available.input.id && recorder != null &&
            controller.state.value.status == BluetoothVoiceStatus.Listening
        ) return
        startListening(available)
    }

    private suspend fun startListening(newRoute: BluetoothAudioRoute) {
        stopAudioCapture(releaseRoute = false)
        val modelPath = controller.modelPath()
        if (modelPath == null) {
            setStatus(BluetoothVoiceStatus.Error, getString(R.string.bluetooth_voice_status_model_missing))
            return
        }
        val activated = runCatching { audioRouter.activate(newRoute) }.getOrDefault(false)
        if (!activated) {
            audioRouter.release()
            setStatus(
                BluetoothVoiceStatus.WaitingForBluetooth,
                getString(R.string.bluetooth_voice_status_bluetooth_audio_unavailable),
            )
            return
        }
        route = newRoute
        val wakeDetector = runCatching {
            withContext(Dispatchers.IO) {
                val model = withTimeout(ACQUIRE_MODEL_TIMEOUT_MS) {
                    wakeModels.acquire(modelPath)
                }
                VoskWakeWordDetector(model, preferences.keyword.value)
            }
        }.getOrElse { error ->
            audioRouter.release()
            route = null
            setStatus(
                BluetoothVoiceStatus.Error,
                error.message ?: getString(R.string.bluetooth_voice_status_model_load_failed),
            )
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
            onError = { error -> scope.launch { recoverFromAudioError(error) } },
        )
        if (!started) {
            recorder = null
            wakeDetector.close()
            detector = null
            audioRouter.release()
            route = null
            setStatus(BluetoothVoiceStatus.Error, getString(R.string.bluetooth_voice_status_mic_start_failed))
            return
        }
        recoveryAttempts = 0
        setStatus(
            BluetoothVoiceStatus.Listening,
            getString(R.string.bluetooth_voice_status_listening, preferences.keyword.value),
            deviceName = newRoute.name,
        )
    }

    private fun onAudioChunk(chunk: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        synchronized(audioLock) {
            preRoll.append(chunk)
            val activeCapture = capture
            if (activeCapture != null) {
                // 提示音播放窗口内的采样不写入指令音频，避免“滴”声串入云端识别。
                if (now < cueActiveUntilMs) return
                when (val decision = activeCapture.append(chunk, now)) {
                    CaptureDecision.Continue -> Unit
                    CaptureDecision.Cancel -> {
                        capture = null
                        detector?.reset()
                        scope.launch {
                            setStatus(
                                BluetoothVoiceStatus.Listening,
                                getString(R.string.bluetooth_voice_status_no_command),
                            )
                        }
                    }
                    is CaptureDecision.Complete -> {
                        capture = null
                        recorder?.stop()
                        recorder = null
                        scope.launch { processCommand(decision.pcm16) }
                    }
                }
                return
            }
            if (controller.state.value.status != BluetoothVoiceStatus.Listening || now - lastWakeAtMs < WAKE_COOLDOWN_MS) {
                return
            }
            val detected = runCatching { detector?.accept(chunk) == true }.getOrElse { error ->
                scope.launch { recoverFromAudioError(error) }
                false
            }
            if (detected) {
                lastWakeAtMs = now
                cueActiveUntilMs = now + WAKE_CUE_GUARD_MS
                capture = VoiceCommandCapture(preRoll.snapshot(), now)
                scope.launch { playWakeCue() }
                scope.launch {
                    setStatus(
                        BluetoothVoiceStatus.CapturingCommand,
                        getString(R.string.bluetooth_voice_status_say_command),
                    )
                }
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

    private fun processCommand(pcm16: ByteArray) {
        processingJob?.cancel()
        processingJob = scope.launch {
            try {
                cancellationAwareRunCatching {
                    setStatus(BluetoothVoiceStatus.Transcribing, getString(R.string.bluetooth_voice_status_transcribing))
                    val transcript = speechRecognition.transcribe(pcm16)
                    val command = stripWakeKeyword(transcript, preferences.keyword.value)
                    check(command.isNotBlank()) { getString(R.string.bluetooth_voice_status_no_task_content) }
                    setStatus(
                        BluetoothVoiceStatus.RunningAgent,
                        getString(
                            R.string.bluetooth_voice_status_running_agent,
                            command.take(NOTIFICATION_PREVIEW_LENGTH),
                        ),
                        lastCommand = command,
                    )
                    val result = agentTasks.execute(command, ::confirmVoiceTool)
                    val activeRoute = runCatching { audioRouter.findRoute() }.getOrNull()
                    if (activeRoute != null && speechPlayer.isAvailable()) {
                        setStatus(
                            BluetoothVoiceStatus.Speaking,
                            getString(R.string.bluetooth_voice_status_speaking),
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
                        error.message ?: getString(R.string.bluetooth_voice_status_task_failed),
                        lastCommand = controller.state.value.lastCommand,
                    )
                    delay(3_000)
                }
            } finally {
                processingJob = null
                if (!pausedByUser) reconcileBluetoothRoute()
            }
        }
    }

    private suspend fun confirmVoiceTool(request: VoiceToolConfirmation): Boolean {
        val activeRoute = runCatching { audioRouter.findRoute() }.getOrNull() ?: return false
        if (!speechPlayer.isAvailable()) return false
        val wakeModel = activeWakeModel()
        setStatus(
            BluetoothVoiceStatus.Speaking,
            getString(R.string.bluetooth_voice_status_confirm_wait, request.toolName),
            deviceName = activeRoute.name,
        )
        val spokenTarget = voiceConfirmationTarget(request.arguments)
        val promptPlayed = speechPlayer.play(
            wakeModel.confirmationPromptTemplate.format(
                listOf(request.toolName, spokenTarget)
                    .filter(String::isNotBlank)
                    .joinToString("，"),
            ),
            activeRoute,
        )
        if (!promptPlayed) return false

        setStatus(
            BluetoothVoiceStatus.CapturingCommand,
            getString(
                R.string.bluetooth_voice_status_confirm_window,
                CONFIRMATION_TIMEOUT_MS / 1000,
            ),
            deviceName = activeRoute.name,
        )
        val audio = CompletableDeferred<ByteArray?>()
        val startedAt = SystemClock.elapsedRealtime()
        val confirmationCapture = VoiceCommandCapture(
            preRoll = ByteArray(0),
            startedAtMs = startedAt,
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
            getString(R.string.bluetooth_voice_status_transcribing_confirmation),
        )
        val transcript = runCatching { speechRecognition.transcribe(pcm16) }.getOrNull() ?: return false
        return isVoiceConfirmationApproved(transcript, wakeModel.confirmWords, wakeModel.rejectWords)
    }

    private fun activeWakeModel(): WakeModelInfo =
        WakeModelCatalog.byId(preferences.activeModelId.value) ?: WakeModelCatalog.default

    private fun recoverFromAudioError(error: Throwable) {
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
            getString(
                R.string.bluetooth_voice_status_retrying,
                delayMs / 1000,
                recoveryAttempts,
                MAX_RECOVERY_ATTEMPTS,
            ),
        )
        recoveryJob = scope.launch {
            delay(delayMs)
            reconcileBluetoothRoute()
        }
    }

    private fun recorderErrorMessage(error: Throwable): String {
        val recorderError = error as? BluetoothRecorderException
            ?: return error.message ?: getString(R.string.bluetooth_voice_status_recorder_failed)
        return when (recorderError.reason) {
            BluetoothRecorderException.Reason.BufferSizeUnavailable ->
                getString(R.string.bluetooth_voice_error_buffer_size)
            BluetoothRecorderException.Reason.MicrophoneRouteFailed ->
                getString(R.string.bluetooth_voice_error_route_failed)
            BluetoothRecorderException.Reason.NotRecording ->
                getString(R.string.bluetooth_voice_error_not_recording)
            BluetoothRecorderException.Reason.ReadFailed ->
                getString(R.string.bluetooth_voice_error_read_failed, recorderError.errorCode ?: 0)
        }
    }

    private fun pauseListening() {
        pausedByUser = true
        recoveryJob?.cancel()
        agentTasks.stop()
        processingJob?.cancel()
        processingJob = null
        stopAudioCapture(releaseRoute = true)
        setStatus(BluetoothVoiceStatus.Paused, getString(R.string.bluetooth_voice_status_paused))
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

    private fun stopCompletely() {
        pausedByUser = true
        recoveryJob?.cancel()
        agentTasks.stop()
        processingJob?.cancel()
        processingJob = null
        stopAudioCapture(releaseRoute = true)
        wakeModels.release()
        controller.setStatus(BluetoothVoiceStatus.Stopped, deviceName = null, message = null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setStatus(
        status: BluetoothVoiceStatus,
        message: String,
        deviceName: String? = controller.state.value.deviceName,
        lastCommand: String? = controller.state.value.lastCommand,
    ) {
        controller.setStatus(status, deviceName, lastCommand, message)
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status, message),
        )
    }

    private fun buildNotification(status: BluetoothVoiceStatus, message: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            controller.state.value.voiceSessionId?.let { putExtra(BluetoothVoiceController.BLUETOOTH_VOICE_EXTRA_SESSION_ID, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAction = if (status == BluetoothVoiceStatus.Paused) {
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_RESUME
        } else {
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_PAUSE
        }
        val pauseLabel = getString(
            if (status == BluetoothVoiceStatus.Paused) {
                R.string.bluetooth_voice_action_resume
            } else {
                R.string.bluetooth_voice_action_pause
            },
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.bluetooth_voice_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setOngoing(status != BluetoothVoiceStatus.Stopped)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseLabel, servicePendingIntent(pauseAction, 2))
            .addAction(
                0,
                getString(R.string.bluetooth_voice_action_stop),
                servicePendingIntent(BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_STOP, 3),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, BluetoothVoiceService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.bluetooth_voice_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasRequiredPermissions(): Boolean = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
    ).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    override fun onDestroy() {
        recoveryJob?.cancel()
        processingJob?.cancel()
        stopAudioCapture(releaseRoute = true)
        wakeModels.release()
        cueToneGenerator?.release()
        cueToneGenerator = null
        audioManager.removeOnModeChangedListener(callModeListener)
        audioRouter.stopObserving()
        if (controller.state.value.status != BluetoothVoiceStatus.Stopped) {
            controller.setStatus(BluetoothVoiceStatus.Stopped, deviceName = null, message = null)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VOICE_SESSION_ID = BluetoothVoiceController.BLUETOOTH_VOICE_EXTRA_SESSION_ID
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_voice_wake"
        private const val NOTIFICATION_ID = 4107
        private const val WAKE_COOLDOWN_MS = 2_000L
        private const val WAKE_CUE_GUARD_MS = 400L
        private const val MAX_RECOVERY_ATTEMPTS = 3
        private const val RECOVERY_BASE_DELAY_MS = 2_000L
        private const val CONFIRMATION_TIMEOUT_MS = 15_000L
        private const val CONFIRMATION_SPEECH_START_TIMEOUT_MS = 5_000L
        private const val ACQUIRE_MODEL_TIMEOUT_MS = 30_000L
        private const val NOTIFICATION_PREVIEW_LENGTH = 120
        private const val TONE_VOLUME_PERCENT = 55
        private const val TONE_DURATION_MS = 150
        private const val TONE_DELAY_MS = 200L
    }
}
