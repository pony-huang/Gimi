package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManifestComponentOwnershipTest {

    @Test
    fun notificationListenerUsesDataAgentOwnedComponentName() {
        val manifest = File("src/main/AndroidManifest.xml")

        assertTrue("App manifest should exist", manifest.isFile)
        val content = manifest.readText()
        assertFalse(
            "The notification listener must not reference the removed agent package",
            content.contains(
                "android:name=\".agent.tools.system.MediaNotificationListenerService\"",
            ),
        )
        assertTrue(
            "The notification listener should reference its data:agent owner package",
            content.contains(
                "github.ponyhuang.gimi.data.agent.tools.system.MediaNotificationListenerService",
            ),
        )
    }

    @Test
    fun voiceAssistantDeclaresOptionalOverlayPermissionAndPrivateLockScreenActivity() {
        val content = File("src/main/AndroidManifest.xml").readText()

        assertTrue(content.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(content.contains("android:name=\".voice.AssistantLockScreenActivity\""))
        assertTrue(content.contains("android:exported=\"false\""))
        assertTrue(content.contains("android:excludeFromRecents=\"true\""))
        assertTrue(content.contains("android:noHistory=\"true\""))
    }
}
