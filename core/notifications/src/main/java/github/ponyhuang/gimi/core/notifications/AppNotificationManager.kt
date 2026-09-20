package github.ponyhuang.gimi.core.notifications

/**
 * Application notifications that may be shown when the app is no longer visible.
 *
 * Implementations silently skip notifications when the user has disabled the
 * notification permission or app notifications.
 */
interface AppNotificationManager {
    fun notifyTaskCompleted()

    fun notifyToolExecution(toolName: String? = null)

    fun notifyToolConfirmation(toolName: String? = null)

    fun notifyTextInput()

    fun notifyChoice()

    fun cancelPendingInteractionNotifications()
}
