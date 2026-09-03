package github.ponyhuang.gimi.data.voicewake.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 语音唤醒模型安装流程使用的系统通知边界。 */
interface WakeModelNotifier {
    fun showDownloading(info: WakeModelInfo, progress: Float, totalBytes: Long)

    fun showInstalling(info: WakeModelInfo)

    fun showReady(info: WakeModelInfo)

    fun showFailed(info: WakeModelInfo)

    fun cancel(modelId: String)
}

/** 使用 Android 通知栏展示语音唤醒模型下载与安装进度。 */
@Singleton
class AndroidWakeModelNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeModelNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val lastProgressUpdates = ConcurrentHashMap<String, Long>()

    @SuppressLint("MissingPermission")
    override fun showDownloading(info: WakeModelInfo, progress: Float, totalBytes: Long) {
        if (!notificationsEnabled()) return
        val normalizedProgress = progress.coerceIn(0f, 1f)
        val nowNanos = System.nanoTime()
        val lastUpdate = lastProgressUpdates[info.id]
        val shouldNotify = normalizedProgress <= 0f ||
            normalizedProgress >= 1f ||
            lastUpdate == null ||
            nowNanos - lastUpdate >= PROGRESS_INTERVAL_NANOS
        if (!shouldNotify) return
        lastProgressUpdates[info.id] = nowNanos

        val percent = (normalizedProgress * 100).toInt().coerceIn(0, 100)
        val indeterminate = normalizedProgress <= 0f
        val contentText = if (indeterminate) {
            context.getString(R.string.wake_model_notification_preparing)
        } else {
            context.getString(
                R.string.wake_model_notification_progress,
                percent,
                formatSize(totalBytes * normalizedProgress),
                formatSize(totalBytes.toFloat()),
            )
        }
        val notification = baseBuilder()
            .setContentTitle(
                context.getString(R.string.wake_model_notification_downloading, info.defaultWakeWord),
            )
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .build()
        manager.notify(notificationId(info.id), notification)
    }

    @SuppressLint("MissingPermission")
    override fun showInstalling(info: WakeModelInfo) {
        if (!notificationsEnabled()) return
        lastProgressUpdates.remove(info.id)
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.wake_model_notification_installing))
            .setContentText(info.defaultWakeWord)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        manager.notify(notificationId(info.id), notification)
    }

    @SuppressLint("MissingPermission")
    override fun showReady(info: WakeModelInfo) {
        if (!notificationsEnabled()) return
        lastProgressUpdates.remove(info.id)
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.wake_model_notification_ready))
            .setContentText(info.defaultWakeWord)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(info.id), notification)
    }

    @SuppressLint("MissingPermission")
    override fun showFailed(info: WakeModelInfo) {
        if (!notificationsEnabled()) return
        lastProgressUpdates.remove(info.id)
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.wake_model_notification_failed))
            .setContentText(info.defaultWakeWord)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(info.id), notification)
    }

    override fun cancel(modelId: String) {
        lastProgressUpdates.remove(modelId)
        manager.cancel(notificationId(modelId))
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setOnlyAlertOnce(true)
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wake_model_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun notificationsEnabled(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    private fun notificationId(modelId: String): Int =
        NOTIFICATION_ID_BASE + (modelId.hashCode() and NOTIFICATION_ID_MASK)

    private fun formatSize(bytes: Float): String = when {
        bytes >= 1024f * 1024f -> "%.1f MB".format(bytes / 1024f / 1024f)
        bytes >= 1024f -> "%.0f KB".format(bytes / 1024f)
        else -> "%.0f B".format(bytes)
    }

    private companion object {
        private const val CHANNEL_ID = "voice_wake_model"
        private const val NOTIFICATION_ID_BASE = 4_300
        private const val NOTIFICATION_ID_MASK = 0x3fff
        private const val PROGRESS_INTERVAL_NANOS = 250_000_000L
    }
}
