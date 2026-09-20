package github.ponyhuang.gimi.core.notifications

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidAppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppNotificationManager {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    override fun notifyTaskCompleted() {
        if (!shouldNotify()) return
        manager.cancel(NOTIFICATION_INTERACTION)
        notify(
            NOTIFICATION_TASK_COMPLETED,
            baseBuilder()
                .setContentTitle(context.getString(R.string.notification_task_completed_title))
                .setContentText(context.getString(R.string.notification_task_completed_text))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyToolExecution(toolName: String?) {
        if (!shouldNotify()) return
        val text = if (toolName.isNullOrBlank()) {
            context.getString(R.string.notification_tool_execution_text)
        } else {
            context.getString(R.string.notification_tool_execution_tool_text, toolName)
        }
        notify(
            NOTIFICATION_INTERACTION,
            baseBuilder()
                .setContentTitle(context.getString(R.string.notification_tool_execution_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyToolConfirmation(toolName: String?) {
        if (!shouldNotify()) return
        val text = if (toolName.isNullOrBlank()) {
            context.getString(R.string.notification_confirmation_text)
        } else {
            context.getString(R.string.notification_confirmation_tool_text, toolName)
        }
        notify(
            NOTIFICATION_INTERACTION,
            baseBuilder()
                .setContentTitle(context.getString(R.string.notification_confirmation_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyTextInput() {
        notifyInteraction(
            R.string.notification_input_title,
            R.string.notification_input_text,
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyChoice() {
        notifyInteraction(
            R.string.notification_choice_title,
            R.string.notification_choice_text,
        )
    }

    override fun cancelPendingInteractionNotifications() {
        manager.cancel(NOTIFICATION_INTERACTION)
    }

    @SuppressLint("MissingPermission")
    private fun notifyInteraction(titleRes: Int, textRes: Int) {
        if (!shouldNotify()) return
        notify(
            NOTIFICATION_INTERACTION,
            baseBuilder()
                .setContentTitle(context.getString(titleRes))
                .setContentText(context.getString(textRes))
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setOngoing(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        manager.notify(id, notification)
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentIntent(createLaunchPendingIntent())
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private fun createLaunchPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: return null
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
    }

    private fun shouldNotify(): Boolean =
        !isAppVisible() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled()

    private fun isAppVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

    private companion object {
        // Channel importance is immutable after creation; v2 upgrades existing installs to heads-up.
        const val CHANNEL_ID = "app_events_v2"
        const val NOTIFICATION_TASK_COMPLETED = 4301
        const val NOTIFICATION_INTERACTION = 4302
        const val REQUEST_OPEN_APP = 4303
    }
}
