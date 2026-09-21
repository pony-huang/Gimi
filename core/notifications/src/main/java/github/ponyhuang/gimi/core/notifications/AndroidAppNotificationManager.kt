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
    private val interactionTags = mutableSetOf<String>()

    @SuppressLint("MissingPermission")
    override fun notifyTaskCompleted(taskId: String?) {
        // 只清理已经完成的任务，不能让并行会话的交互通知被先完成的任务误删。
        cancelPendingInteractionNotifications(taskId)
        if (!shouldNotify()) return
        notify(
            taskTag(taskId),
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
    override fun notifyToolExecution(toolName: String?, taskId: String?) {
        if (!shouldNotify()) return
        val text = if (toolName.isNullOrBlank()) {
            context.getString(R.string.notification_tool_execution_text)
        } else {
            context.getString(R.string.notification_tool_execution_tool_text, toolName)
        }
        notifyInteraction(
            taskId,
            R.string.notification_tool_execution_title,
            text,
            android.R.drawable.stat_notify_sync,
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyToolConfirmation(toolName: String?, taskId: String?) {
        if (!shouldNotify()) return
        val text = if (toolName.isNullOrBlank()) {
            context.getString(R.string.notification_confirmation_text)
        } else {
            context.getString(R.string.notification_confirmation_tool_text, toolName)
        }
        notifyInteraction(
            taskId,
            R.string.notification_confirmation_title,
            text,
            android.R.drawable.stat_sys_warning,
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyTextInput(taskId: String?) {
        notifyInteraction(
            taskId,
            R.string.notification_input_title,
            context.getString(R.string.notification_input_text),
            android.R.drawable.stat_notify_more,
        )
    }

    @SuppressLint("MissingPermission")
    override fun notifyChoice(taskId: String?) {
        notifyInteraction(
            taskId,
            R.string.notification_choice_title,
            context.getString(R.string.notification_choice_text),
            android.R.drawable.stat_notify_more,
        )
    }

    override fun cancelPendingInteractionNotifications(taskId: String?) {
        val tags = synchronized(interactionTags) {
            if (taskId == null) {
                interactionTags.toList().also { interactionTags.clear() }
            } else {
                listOf(taskTag(taskId)).also { interactionTags.removeAll(it.toSet()) }
            }
        }
        tags.forEach { tag -> manager.cancel(tag, NOTIFICATION_INTERACTION) }
    }

    @SuppressLint("MissingPermission")
    private fun notifyInteraction(
        taskId: String?,
        titleRes: Int,
        text: String,
        icon: Int,
    ) {
        if (!shouldNotify()) return
        val tag = taskTag(taskId)
        synchronized(interactionTags) { interactionTags += tag }
        notify(
            tag,
            NOTIFICATION_INTERACTION,
            baseBuilder()
                .setContentTitle(context.getString(titleRes))
                .setContentText(text)
                .setSmallIcon(icon)
                .setOngoing(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(tag: String, id: Int, notification: android.app.Notification) {
        manager.notify(tag, id, notification)
    }

    private fun taskTag(taskId: String?): String =
        taskId?.takeIf(String::isNotBlank)?.let { "agent-task:$it" } ?: DEFAULT_TASK_TAG

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
        const val DEFAULT_TASK_TAG = "agent-task:default"
        const val NOTIFICATION_TASK_COMPLETED = 4301
        const val NOTIFICATION_INTERACTION = 4302
        const val REQUEST_OPEN_APP = 4303
    }
}
