package github.ponyhuang.gimi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPromptPolicyTest {
    @Test
    fun freshInstallOnAndroid13RequestsNotificationPermission() {
        assertTrue(shouldRequestNotificationPermission(33, granted = false, previouslyRequested = false))
    }

    @Test
    fun previouslyDeniedPermissionDoesNotPromptOnEveryLaunch() {
        assertFalse(shouldRequestNotificationPermission(35, granted = false, previouslyRequested = true))
    }

    @Test
    fun grantedPermissionAndOlderAndroidDoNotPrompt() {
        assertFalse(shouldRequestNotificationPermission(35, granted = true, previouslyRequested = false))
        assertFalse(shouldRequestNotificationPermission(32, granted = false, previouslyRequested = false))
    }
}
