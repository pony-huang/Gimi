package github.ponyhuang.asssistantai.voice

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
import github.ponyhuang.asssistantai.MainActivity
import github.ponyhuang.asssistantai.R
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.domain.speech.usecase.markdownToSpeechText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BluetoothVoiceService : Service() {
    @Inject lateinit var controller: BluetoothVoiceController
    @Inject lateinit var preferences: BluetoothVoicePreferences
    @Inject lateinit var audioRouter: BluetoothAudioRouter
    @Inject lateinit var speechRecognition: SpeechRecognitionRepository
    @Inject lateinit var agentTasks: VoiceAgentTaskExecutor
    @Inject lateinit var speechPlayer: VoiceSpeechPlayer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioLock = Any()
    private var route: BluetoothAudioRoute? = null
    private var recorder: BluetoothPcmRecorder? = null
    private var detector: VoskWakeWordDetector? = null
    private var preRoll = PcmPreRollBuffer()
    private var capture: VoiceCommandCapture? = null
    private var processingJob: Job? = null
    private var pausedByUser = false
    private var lastWakeAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioRouter.observe { scope.launch { reconcileBluetoothRoute() } }
        scope.launch {
            preferences.keyword.collect { keyword ->
                withContext(Dispatchers.IO) { detector?.updateKeyword(keyword) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> stopCompletely()
            ACTION_PAUSE -> pauseListening()
            ACTION_RESUME -> {
                pausedByUser = false
                scope.launch { reconcileBluetoothRoute() }
            }
            ACTION_START -> startForegroundAndListen()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundAndListen() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(BluetoothVoiceStatus.Starting, "正在启动监听"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (!hasRequiredPermissions()) {
            setStatus(BluetoothVoiceStatus.Error, "缺少麦克风或蓝牙权限")
            return
        }
        pausedByUser = false
        scope.launch { reconcileBluetoothRoute() }
    }

    private suspend fun reconcileBluetoothRoute() {
        if (pausedByUser || processingJob?.isActive == true) return
        if (!hasRequiredPermissions()) {
            setStatus(BluetoothVoiceStatus.Error, "缺少麦克风或蓝牙权限")
            return
        }
        val available = runCatching { audioRouter.findRoute() }.getOrNull()
        if (available == null) {
            stopAudioCapture(releaseRoute = true)
            setStatus(BluetoothVoiceStatus.WaitingForBluetooth, "请连接支持通话麦克风的蓝牙耳机")
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
            setStatus(BluetoothVoiceStatus.Error, "唤醒模型尚未安装")
            return
        }
        val activated = runCatching { audioRouter.activate(newRoute) }.getOrDefault(false)
        if (!activated) {
            audioRouter.release()
            setStatus(BluetoothVoiceStatus.WaitingForBluetooth, "无法启用蓝牙通话音频")
            return
        }
        route = newRoute
        val wakeDetector = runCatching {
            withContext(Dispatchers.IO) {
                VoskWakeWordDetector(modelPath, preferences.keyword.value)
            }
        }.getOrElse { error ->
            audioRouter.release()
            route = null
            setStatus(BluetoothVoiceStatus.Error, error.message ?: "无法加载唤醒模型")
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
            setStatus(BluetoothVoiceStatus.Error, "蓝牙麦克风启动失败")
            return
        }
        setStatus(
            BluetoothVoiceStatus.Listening,
            "正在监听“${preferences.keyword.value}”",
            deviceName = newRoute.name,
        )
    }

    private fun onAudioChunk(chunk: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        synchronized(audioLock) {
            preRoll.append(chunk)
            val activeCapture = capture
            if (activeCapture != null) {
                when (val decision = activeCapture.append(chunk, now)) {
                    CaptureDecision.Continue -> Unit
                    CaptureDecision.Cancel -> {
                        capture = null
                        detector?.reset()
                        scope.launch {
                            setStatus(BluetoothVoiceStatus.Listening, "未听到任务，继续监听")
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
                capture = VoiceCommandCapture(preRoll.snapshot(), now)
                scope.launch { playWakeCue() }
                scope.launch {
                    setStatus(BluetoothVoiceStatus.CapturingCommand, "请说出任务")
                }
            }
        }
    }

    private suspend fun playWakeCue() {
        val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55)
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            delay(200)
        } finally {
            tone.release()
        }
    }

    private fun processCommand(pcm16: ByteArray) {
        processingJob?.cancel()
        processingJob = scope.launch {
            try {
                setStatus(BluetoothVoiceStatus.Transcribing, "正在识别任务")
                val transcript = speechRecognition.transcribe(pcm16)
                val command = stripWakeKeyword(transcript, preferences.keyword.value)
                check(command.isNotBlank()) { "没有识别到唤醒词后的任务内容" }
                setStatus(
                    BluetoothVoiceStatus.RunningAgent,
                    "正在执行：${command.take(NOTIFICATION_PREVIEW_LENGTH)}",
                    lastCommand = command,
                )
                val result = agentTasks.execute(command)
                val activeRoute = runCatching { audioRouter.findRoute() }.getOrNull()
                if (activeRoute != null && speechPlayer.isAvailable()) {
                    setStatus(
                        BluetoothVoiceStatus.Speaking,
                        "正在播报任务结果",
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
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                setStatus(
                    BluetoothVoiceStatus.Error,
                    error.message ?: "语音任务执行失败",
                    lastCommand = controller.state.value.lastCommand,
                )
                delay(3_000)
            } finally {
                processingJob = null
                if (!pausedByUser) reconcileBluetoothRoute()
            }
        }
    }

    private suspend fun recoverFromAudioError(error: Throwable) {
        stopAudioCapture(releaseRoute = true)
        setStatus(BluetoothVoiceStatus.Error, error.message ?: "蓝牙录音失败")
    }

    private fun pauseListening() {
        pausedByUser = true
        processingJob?.cancel()
        processingJob = null
        stopAudioCapture(releaseRoute = true)
        setStatus(BluetoothVoiceStatus.Paused, "监听已暂停")
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
        processingJob?.cancel()
        processingJob = null
        stopAudioCapture(releaseRoute = true)
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
            controller.state.value.voiceSessionId?.let { putExtra(EXTRA_VOICE_SESSION_ID, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAction = if (status == BluetoothVoiceStatus.Paused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (status == BluetoothVoiceStatus.Paused) "继续" else "暂停"
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
            .addAction(0, "停止", servicePendingIntent(ACTION_STOP, 3))
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
        processingJob?.cancel()
        stopAudioCapture(releaseRoute = true)
        audioRouter.stopObserving()
        if (controller.state.value.status != BluetoothVoiceStatus.Stopped) {
            controller.setStatus(BluetoothVoiceStatus.Stopped, deviceName = null, message = null)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "github.ponyhuang.asssistantai.voice.START"
        const val ACTION_STOP = "github.ponyhuang.asssistantai.voice.STOP"
        const val ACTION_PAUSE = "github.ponyhuang.asssistantai.voice.PAUSE"
        const val ACTION_RESUME = "github.ponyhuang.asssistantai.voice.RESUME"
        const val EXTRA_VOICE_SESSION_ID = "voice_session_id"
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_voice_wake"
        private const val NOTIFICATION_ID = 4107
        private const val WAKE_COOLDOWN_MS = 2_000L
        private const val NOTIFICATION_PREVIEW_LENGTH = 120
    }
}
