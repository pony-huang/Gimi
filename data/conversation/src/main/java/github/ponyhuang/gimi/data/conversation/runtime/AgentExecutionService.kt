package github.ponyhuang.gimi.data.conversation.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.data.conversation.R
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台服务：在 Agent 任务运行期间维持进程前台优先级，
 * 申请 Partial WakeLock 并防止后台网络受限或进程被挂起。
 *
 * 监听 [AgentRuntimeGate.state]，当所有任务结束（回到 [AgentRuntimeState.Idle]）时自动退出。
 */
@AndroidEntryPoint
class AgentExecutionService : Service() {

    @Inject
    lateinit var runtimeGate: AgentRuntimeGate

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        acquireWakeLock()

        val notification = createNotification(getString(R.string.agent_execution_phase_generating))
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        serviceScope.launch {
            runtimeGate.state.collect { state ->
                when (state) {
                    is AgentRuntimeState.Busy -> {
                        val activePhase = state.tasks.lastOrNull()?.phase ?: AgentTaskPhase.GENERATING
                        val text = phaseToText(activePhase)
                        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
                    }
                    AgentRuntimeState.Idle -> {
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (runtimeGate.state.value is AgentRuntimeState.Idle) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.agent_execution_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.agent_execution_service_channel_name)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.agent_execution_service_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun phaseToText(phase: AgentTaskPhase): String = when (phase) {
        AgentTaskPhase.GENERATING -> getString(R.string.agent_execution_phase_generating)
        AgentTaskPhase.EXECUTING_TOOL -> getString(R.string.agent_execution_phase_tool)
        AgentTaskPhase.WAITING_FOR_CONFIRMATION -> getString(R.string.agent_execution_phase_confirmation)
        AgentTaskPhase.WAITING_FOR_INPUT -> getString(R.string.agent_execution_phase_input)
    }

    companion object {
        private const val TAG = "AgentExecutionService"
        private const val CHANNEL_ID = "agent_execution"
        private const val NOTIFICATION_ID = 4202
        private const val WAKELOCK_TAG = "asssistantai:AgentExecution"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, AgentExecutionService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start AgentExecutionService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AgentExecutionService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop AgentExecutionService", e)
            }
        }
    }
}
