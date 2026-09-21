package github.ponyhuang.gimi.core.notifications

/**
 * Application notifications that may be shown when the app is no longer visible.
 *
 * Implementations silently skip notifications when the user has disabled the
 * notification permission or app notifications.
 */
interface AppNotificationManager {
    fun notifyTaskCompleted(taskId: String? = null)

    fun notifyToolExecution(toolName: String? = null, taskId: String? = null)

    fun notifyToolConfirmation(toolName: String? = null, taskId: String? = null)

    fun notifyTextInput(taskId: String? = null)

    fun notifyChoice(taskId: String? = null)

    fun cancelPendingInteractionNotifications(taskId: String? = null)
}
