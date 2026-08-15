package github.ponyhuang.gimi.data.appupdate.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import github.ponyhuang.gimi.data.appupdate.R
import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import java.io.File

/**
 * 下载期间的常驻进度通知。通知权限未授予时所有方法静默跳过，
 * 不影响下载与对话框流程。
 */
internal class UpdateNotifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    // 运行时由 notificationsEnabled() 显式守卫；lint 无法跨方法识别，故抑制。
    @SuppressLint("MissingPermission")
    fun showProgress(info: AppUpdateInfo, progress: Float, totalBytes: Long) {
        if (!notificationsEnabled()) return
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CANCEL,
            Intent(context, UpdateActionReceiver::class.java)
                .setAction(UpdateActionReceiver.ACTION_CANCEL_DOWNLOAD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.app_update_downloading_title, info.tagName))
            .setContentText(
                context.getString(
                    R.string.app_update_downloading_progress,
                    percent,
                    formatSize(totalBytes * progress),
                    formatSize(totalBytes.toFloat()),
                ),
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.app_update_cancel), cancelIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSize(bytes: Float): String = when {
        bytes >= 1024f * 1024f -> "%.1f MB".format(bytes / 1024f / 1024f)
        bytes >= 1024f -> "%.0f KB".format(bytes / 1024f)
        else -> "%.0f B".format(bytes)
    }

    @SuppressLint("MissingPermission")
    fun showCompleted(info: AppUpdateInfo, apk: File) {
        if (!notificationsEnabled()) return
        val installIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_INSTALL,
            Intent(context, UpdateActionReceiver::class.java)
                .setAction(UpdateActionReceiver.ACTION_INSTALL)
                .putExtra(UpdateActionReceiver.EXTRA_APK_PATH, apk.absolutePath),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.app_update_download_complete_title))
            .setContentText(context.getString(R.string.app_update_download_complete_text, info.tagName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(installIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun showFailed(info: AppUpdateInfo) {
        if (!notificationsEnabled()) return
        val notification = baseBuilder()
            .setContentTitle(context.getString(R.string.app_update_download_failed))
            .setContentText(info.tagName)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setOnlyAlertOnce(true)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_update_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun notificationsEnabled(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    companion object {
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CANCEL = 1
        private const val REQUEST_INSTALL = 2
    }
}
