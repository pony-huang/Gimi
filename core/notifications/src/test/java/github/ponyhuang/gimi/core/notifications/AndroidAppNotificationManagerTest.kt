package github.ponyhuang.gimi.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 回归覆盖并行会话下通知 tag 的登记与取消语义：
 * 一个任务的完成不得误删其他任务的交互通知；全量清理要同时覆盖交互与完成两类。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAppNotificationManagerTest {
    private val context = RuntimeEnvironment.getApplication() as Application
    private val systemManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotificationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun activeTags(): List<String> = systemManager.activeNotifications.map { it.tag }

    @Test
    fun completingOneTaskKeepsOtherTasksInteractionNotifications() {
        val manager = AndroidAppNotificationManager(context)

        manager.notifyToolExecution(toolName = "browser", taskId = "t1")
        manager.notifyToolExecution(toolName = "shell", taskId = "t2")
        assertEquals(2, systemManager.activeNotifications.size)

        manager.notifyTaskCompleted(taskId = "t1")

        // t1 的交互通知被完成通知收敛，t2 的交互通知原样保留。
        assertEquals(2, systemManager.activeNotifications.size)
        assertTrue(activeTags().any { it.contains("t2") })
    }

    @Test
    fun cancelAllClearsBothInteractionAndCompletedNotifications() {
        val manager = AndroidAppNotificationManager(context)

        manager.notifyToolExecution(toolName = null, taskId = "t1")
        manager.notifyTaskCompleted(taskId = "t2")

        manager.cancelPendingInteractionNotifications(taskId = null)

        assertEquals(0, systemManager.activeNotifications.size)
    }

    @Test
    fun cancelByTaskOnlyAffectsThatTask() {
        val manager = AndroidAppNotificationManager(context)

        manager.notifyToolConfirmation(toolName = null, taskId = "t1")
        manager.notifyTaskCompleted(taskId = "t1")
        manager.notifyToolExecution(toolName = null, taskId = "t2")

        manager.cancelPendingInteractionNotifications(taskId = "t1")

        assertEquals(1, systemManager.activeNotifications.size)
        assertTrue(activeTags().single().contains("t2"))
    }
}
