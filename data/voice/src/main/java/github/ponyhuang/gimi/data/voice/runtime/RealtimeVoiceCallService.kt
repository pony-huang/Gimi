package github.ponyhuang.gimi.data.voice.runtime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.data.voice.R
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 通话期间维持麦克风型前台优先级；常驻通知提供返回、静音与结束三个入口。
 * 服务只消费共享通话状态，不持有会话：状态复位为无通话后自行退出并移除通知。
 * 前台提升放在每次 start 请求中执行，已运行的服务被降级或首次提升失败后仍能补齐。
 */
@AndroidEntryPoint
class RealtimeVoiceCallService : Service() {
    @Inject lateinit var controller: RealtimeVoiceCallController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val backgrounded = MutableStateFlow(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundPromoted = false

    // 服务自身没有 Activity 生命周期；进程级前后台决定「后台运行中」文案与那一次轻提醒。
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { backgrounded.value = false }
        override fun onStop(owner: LifecycleOwner) { backgrounded.value = true }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // 服务可能在应用已处于后台时被创建（系统重建等）；先按进程实际状态取初值，
        // 否则 onStop 不会再次分发，通知会永远漏掉「后台运行中」。
        backgrounded.value = !ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        acquireWakeLock()
        serviceScope.launch {
            // 部分唤醒锁带超时，定时续期才能覆盖长时间通话；持有时 acquire() 直接返回。
            while (true) {
                delay(WAKELOCK_REFRESH_MS.milliseconds)
                acquireWakeLock()
            }
        }
        serviceScope.launch {
            var previous: VoiceCallNotificationContent? = null
            // 电平每几十毫秒变化；先压缩成通知快照再去重，避免持续刷新通知。
            combine(controller.state, backgrounded) { state, inBackground ->
                state.toNotificationContent(inBackground)
            }.distinctUntilChanged().collect { content ->
                if (!content.active && !content.ending) {
                    stopSelf()
                    return@collect
                }
                showNotification(
                    notification(
                        content,
                        alert = shouldAlertOnBackground(previous, content),
                        initial = previous == null,
                    ),
                )
                previous = content
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 状态栏的三个入口与页面操作汇合到同一个控制器，走同一条清理链。
        when (intent?.action) {
            ACTION_END -> controller.end()
            ACTION_TOGGLE_MUTE -> controller.toggleMute()
        }
        val state = controller.state.value
        // 必须先提升前台再判断退出：本服务经由 startForegroundService 启动，
        // 未在时限内调用 startForeground 会触发 ForegroundServiceDidNotStartInTimeException。
        startForegroundSafely()
        if (!state.active && !state.ending) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        // 每次 start 请求都重新提升：ensureForeground() 对已运行的服务只会走到这里，
        // 首次提升失败或服务被系统降级后，应用重新可见即可恢复通知与麦克风优先级。
        // 进程死亡后不恢复：没有跨进程可恢复的音频会话，空白服务反而会占用麦克风。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely() {
        val content = controller.state.value.toNotificationContent(backgrounded.value)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(content, alert = false, initial = !foregroundPromoted),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            foregroundPromoted = true
        } catch (error: Exception) {
            // 权限或启动时机受限时无法建立麦克风型前台服务；从未成功提升过则不能留空白服务常驻。
            Log.w(TAG, "Cannot promote voice call to foreground", error)
            if (!foregroundPromoted) stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(notification: Notification) {
        // 没有 POST_NOTIFICATIONS 时系统静默丢弃通知；权限由通话入口负责申请与提示。
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun notification(
        content: VoiceCallNotificationContent,
        alert: Boolean,
        initial: Boolean,
    ): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RETURN_TO_VOICE_CALL, true)
        }
        val openPending = open?.let {
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val title = if (content.conversationTitle.isBlank()) {
            getString(R.string.voice_call_notification_title)
        } else {
            getString(R.string.voice_call_notification_title_conversation, content.conversationTitle)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voice_call)
            .setContentTitle(title)
            .setContentText(textFor(content.text))
            .setContentIntent(openPending)
            .setOngoing(true)
            // 首次出现（通话开始时仍在前台）静默；只有切后台那次允许渠道默认的轻提示。
            .setOnlyAlertOnce(!alert)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // PRIVATE 使锁屏只显示应用名，不暴露会话标题；需要时解锁查看完整内容。
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (content.backgrounded) builder.setSubText(getString(R.string.voice_call_notification_background))
        if (initial) builder.setSilent(true)
        if (content.active && !content.ending) addCallActions(builder, content, openPending)
        return builder.build()
    }

    private fun addCallActions(
        builder: NotificationCompat.Builder,
        content: VoiceCallNotificationContent,
        openPending: PendingIntent?,
    ) {
        val toggleMute = Intent(this, RealtimeVoiceCallService::class.java)
            .setAction(ACTION_TOGGLE_MUTE)
            .setData(Uri.parse("gimi://realtime-voice/toggle-mute"))
        val toggleMutePending = PendingIntent.getService(
            this,
            REQUEST_MUTE,
            toggleMute,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (content.muted) {
            builder.addAction(
                R.drawable.ic_voice_call,
                getString(R.string.voice_call_notification_unmute),
                toggleMutePending,
            )
        } else {
            builder.addAction(
                R.drawable.ic_voice_call_mute,
                getString(R.string.voice_call_notification_mute),
                toggleMutePending,
            )
        }
        if (openPending != null) {
            builder.addAction(
                R.drawable.ic_voice_call_return,
                getString(R.string.voice_call_notification_return),
                openPending,
            )
        }
        val end = Intent(this, RealtimeVoiceCallService::class.java)
            .setAction(ACTION_END)
            .setData(Uri.parse("gimi://realtime-voice/end"))
        val endPending = PendingIntent.getService(
            this,
            REQUEST_END,
            end,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(R.drawable.ic_voice_call_end, getString(R.string.voice_call_notification_end), endPending)
    }

    private fun textFor(text: VoiceCallNotificationText): String = getString(
        when (text) {
            VoiceCallNotificationText.Idle -> R.string.voice_call_notification_text
            VoiceCallNotificationText.Listening -> R.string.voice_call_notification_listening
            VoiceCallNotificationText.Transcribing -> R.string.voice_call_notification_transcribing
            VoiceCallNotificationText.Thinking -> R.string.voice_call_notification_thinking
            VoiceCallNotificationText.Speaking -> R.string.voice_call_notification_speaking
            VoiceCallNotificationText.Muted -> R.string.voice_call_notification_muted
            VoiceCallNotificationText.Failed -> R.string.voice_call_notification_failed
            VoiceCallNotificationText.Ending -> R.string.voice_call_notification_ending
        },
    )

    private fun ensureNotificationChannel() {
        // 渠道 importance 创建后不可变：旧版本以 IMPORTANCE_LOW 创建，升级安装需要换 ID 才能提权，
        // 与 core:notifications 的 app_events_v2 采用同一迁移方式。
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.voice_call_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.voice_call_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        try {
            val lock = wakeLock ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                .also {
                    it.setReferenceCounted(false)
                    wakeLock = it
                }
            // 非引用计数的锁重复 acquire 会重置超时计时；刷新循环借此在超时前续期，
            // 覆盖长时间通话，不能因为已持有就跳过。
            lock.acquire(WAKELOCK_TIMEOUT_MS)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", error)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to release wake lock", error)
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "RealtimeVoiceCallService"
        private const val CHANNEL_ID = "realtime_voice_call_v2"
        private const val LEGACY_CHANNEL_ID = "realtime_voice_call"
        private const val NOTIFICATION_ID = 4203
        private const val WAKELOCK_TAG = "asssistantai:RealtimeVoiceCall"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKELOCK_REFRESH_MS = 5 * 60 * 1000L
        private const val REQUEST_OPEN = 0
        private const val REQUEST_END = 1
        private const val REQUEST_MUTE = 2
        const val ACTION_END = "github.ponyhuang.gimi.data.voice.action.END_CALL"
        const val ACTION_TOGGLE_MUTE = "github.ponyhuang.gimi.data.voice.action.TOGGLE_MUTE"
        const val EXTRA_RETURN_TO_VOICE_CALL = "github.ponyhuang.gimi.data.voice.extra.RETURN_TO_VOICE_CALL"
    }
}
