package github.ponyhuang.gimi.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.MainActivity
import github.ponyhuang.gimi.MainActivityVisibility
import github.ponyhuang.gimi.R
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceController
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceStatus
import github.ponyhuang.gimi.data.voicewake.VoiceAudioPipeline
import github.ponyhuang.gimi.data.voicewake.VoicePipelineEvent
import github.ponyhuang.gimi.data.voicewake.assistantPresentationEvent
import github.ponyhuang.gimi.domain.assistant.model.AssistantSurfaceEnvironment
import github.ponyhuang.gimi.domain.assistant.model.AssistantSurfaceTarget
import github.ponyhuang.gimi.domain.assistant.model.routeAssistantSurface
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Android 前台服务边界：只处理生命周期、权限、Intent、通话抢占和通知投射。 */
@AndroidEntryPoint
class BluetoothVoiceService : Service() {
    @Inject lateinit var controller: BluetoothVoiceController
    @Inject lateinit var pipeline: VoiceAudioPipeline
    @Inject lateinit var assistantCoordinator: AssistantSessionCoordinator
    @Inject lateinit var assistantOverlayWindow: AssistantOverlayWindow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private var lockScreenRequested = false
    private val callModeListener = AudioManager.OnModeChangedListener { mode ->
        val callActive = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE
        if (callActive) {
            pipeline.pauseForCall()
        } else {
            pipeline.resumeAfterCall()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(this), callModeListener)
        scope.launch {
            pipeline.events.collect(::renderPipelineEvent)
        }
        scope.launch {
            combine(
                assistantCoordinator.state,
                MainActivityVisibility.foreground,
            ) { state, appForeground -> state to appForeground }
                .collect { (state, appForeground) ->
                    renderAssistantSurface(state.presentationVisible, appForeground)
                }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_STOP -> stopCompletely()
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_PAUSE -> pipeline.pause()
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_RESUME -> pipeline.resume()
            BluetoothVoiceController.BLUETOOTH_VOICE_ACTION_START -> startForegroundAndListen()
            // 系统/OEM 可能以空 Intent 重建服务；绝不能默认当作启动监听，
            // 否则用户关闭唤醒后服务被意外拉起会重新占用麦克风。
            null -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundAndListen() {
        val startingMessage = getString(R.string.bluetooth_voice_status_starting)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(BluetoothVoiceStatus.Starting, startingMessage),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (!hasRequiredPermissions()) {
            publishServiceStatus(
                BluetoothVoiceStatus.Error,
                getString(R.string.bluetooth_voice_status_permission_missing),
            )
            return
        }
        controller.setStatus(BluetoothVoiceStatus.Starting, message = startingMessage)
        pipeline.start()
        if (isCallActive()) pipeline.pauseForCall()
    }

    private fun renderPipelineEvent(event: VoicePipelineEvent) {
        event.assistantPresentationEvent()?.let(assistantCoordinator::updatePresentation)
        if (event.status == BluetoothVoiceStatus.Stopped) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(event.status, VoiceNotificationText.forEvent(event)),
        )
    }

    private fun renderAssistantSurface(presentationVisible: Boolean, appForeground: Boolean) {
        val overlayGranted = Settings.canDrawOverlays(this)
        val target = routeAssistantSurface(
            AssistantSurfaceEnvironment(
                presentationVisible = presentationVisible,
                appForeground = appForeground,
                chatVisible = false,
                deviceLocked = keyguardManager.isKeyguardLocked,
                overlayPermissionGranted = overlayGranted,
                // SYSTEM_ALERT_WINDOW 是 Android 后台 Activity 启动的明确豁免条件之一。
                lockScreenLaunchAllowed = overlayGranted,
            ),
        )
        when (target) {
            AssistantSurfaceTarget.SYSTEM_OVERLAY -> {
                lockScreenRequested = false
                assistantOverlayWindow.show()
            }
            AssistantSurfaceTarget.LOCK_SCREEN_ACTIVITY -> {
                assistantOverlayWindow.hide()
                if (!lockScreenRequested) {
                    lockScreenRequested = true
                    runCatching {
                        startActivity(
                            Intent(this, AssistantLockScreenActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                                ),
                        )
                    }.onFailure { lockScreenRequested = false }
                }
            }
            else -> {
                lockScreenRequested = false
                assistantOverlayWindow.hide()
            }
        }
    }

    private fun publishServiceStatus(status: BluetoothVoiceStatus, message: String) {
        controller.setStatus(status, message = message)
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status, message),
        )
    }

    private fun stopCompletely() {
        pipeline.stop()
        assistantCoordinator.hidePresentation()
        assistantOverlayWindow.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(status: BluetoothVoiceStatus, message: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            setAction(MainActivity.ACTION_OPEN_CURRENT_CHAT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
    ).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun isCallActive(): Boolean =
        audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_RINGTONE

    override fun onDestroy() {
        pipeline.stop()
        assistantOverlayWindow.hide()
        audioManager.removeOnModeChangedListener(callModeListener)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_voice_wake"
        private const val NOTIFICATION_ID = 4107
    }
}
