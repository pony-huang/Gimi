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

    // 交互与完成通知都按任务 tag 登记，取消时才能覆盖两类通知，且不误删并行会话的通知。
    private val interactionTags = mutableSetOf<String>()
    private val completedTags = mutableSetOf<String>()

    private val tagsLock = Any()

    init {
        createChannel()
    }

    @SuppressLint("MissingPermission")
    override fun notifyTaskCompleted(taskId: String?) {
        val tag = taskTag(taskId)
        // 只清理已经完成的任务，不能让并行会话的交互通知被先完成的任务误删。
        cancelTaskNotifications(tag)
        if (!shouldNotify()) return
        synchronized(tagsLock) {
            completedTags += tag
            // 完成通知是低打扰的状态确认：可见但不响铃、不 heads-up；notify 也放进锁内，
            // 保证「登记-发出」与并发会话的「取消」串行化，发出后不会被旧 cancel 误删。
            notify(
                tag,
                NOTIFICATION_TASK_COMPLETED,
                baseBuilder()
                    .setContentTitle(context.getString(R.string.notification_task_completed_title))
                    .setContentText(context.getString(R.string.notification_task_completed_text))
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .build(),
            )
        }
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
        val tags = if (taskId == null) null else listOf(taskTag(taskId))
        synchronized(tagsLock) {
            // 取消与登记/发出在同一把锁内完成：并行会话重新发出的通知不会被先到的 cancel 误删。
            val targets = tags ?: (interactionTags + completedTags).toList()
            interactionTags.removeAll(targets.toSet())
            completedTags.removeAll(targets.toSet())
            targets.forEach { tag ->
                manager.cancel(tag, NOTIFICATION_INTERACTION)
                manager.cancel(tag, NOTIFICATION_TASK_COMPLETED)
            }
        }
    }

    /** 只取消单个任务的两类通知，供完成路径收敛该任务的交互通知。 */
    private fun cancelTaskNotifications(tag: String) {
        synchronized(tagsLock) {
            interactionTags.remove(tag)
            completedTags.remove(tag)
            manager.cancel(tag, NOTIFICATION_INTERACTION)
            manager.cancel(tag, NOTIFICATION_TASK_COMPLETED)
        }
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
        synchronized(tagsLock) {
            interactionTags += tag
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
    }

    @SuppressLint("MissingPermission")
    private fun notify(tag: String, id: Int, notification: android.app.Notification) {
        manager.notify(tag, id, notification)
    }

    private fun taskTag(taskId: String?): String =
        taskId?.takeIf(String::isNotBlank)?.let { "agent-task:$it" } ?: DEFAULT_TASK_TAG

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentIntent(createLaunchPendingIntent())
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

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

    private fun createChannel() {
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
