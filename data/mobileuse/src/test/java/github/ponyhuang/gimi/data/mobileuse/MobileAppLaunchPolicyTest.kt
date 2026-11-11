package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileAppLaunchPolicyTest {
    @Test
    fun visibleMainScreenAndForeignDisplayTasksCannotBeTakenOver() {
        val tasks = listOf(MobileAppTask(0, 0, "app", "login", true))
        assertEquals(MobileAppLaunchPolicy.MAIN_SCREEN_BUSY, MobileAppLaunchPolicy.rejection(tasks, "app", 0, 7))
        assertEquals(MobileAppLaunchPolicy.MAIN_SCREEN_BUSY, MobileAppLaunchPolicy.rejection(tasks, "login", 0, 7))
        assertEquals(MobileAppLaunchPolicy.OTHER_DISPLAY_BUSY,
            MobileAppLaunchPolicy.rejection(listOf(tasks.single().copy(displayId = 8, visible = false)), "app", 0, 7))
    }

    @Test
    fun ownDisplayBackgroundMainTaskAndOtherUserCopyRemainIndependent() {
        val task = MobileAppTask(0, 0, "app", "app", false)
        assertNull(MobileAppLaunchPolicy.rejection(listOf(task), "app", 0, 7))
        assertNull(MobileAppLaunchPolicy.rejection(listOf(task.copy(displayId = 7, visible = true)), "app", 0, 7))
        assertNull(MobileAppLaunchPolicy.rejection(listOf(task.copy(userId = 10, visible = true)), "app", 0, 7))
    }
}
