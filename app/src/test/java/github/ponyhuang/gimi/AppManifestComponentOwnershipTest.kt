package github.ponyhuang.gimi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 源 Manifest 声明契约；不代表已验证依赖合并后的 APK Manifest。 */
class AppManifestComponentOwnershipTest {
    @Test
    fun sourceManifestBindsTheUnexportedFileProviderToTheCheckedPathsXml() {
        val providers = sourceXml("src/main/AndroidManifest.xml").elements("provider")
            .filter { it.androidAttribute("name") == "androidx.core.content.FileProvider" }
        assertEquals(1, providers.size)
        val provider = providers.single()
        assertEquals("false", provider.androidAttribute("exported"))
        assertEquals("true", provider.androidAttribute("grantUriPermissions"))
        val metadata = provider.getElementsByTagName("meta-data")
        val pathMetadata = (0 until metadata.length)
            .map { metadata.item(it) as org.w3c.dom.Element }
            .filter { it.androidAttribute("name") == "android.support.FILE_PROVIDER_PATHS" }
        assertEquals(1, pathMetadata.size)
        assertEquals("@xml/file_paths", pathMetadata.single().androidAttribute("resource"))
    }

    @Test
    fun sourceManifestDeclaresTheOwnedNotificationListenerAndItsBinding() {
        val services = sourceXml("src/main/AndroidManifest.xml").elements("service")
        val listeners = services.filter {
            it.androidAttribute("permission") == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        }
        assertEquals(1, listeners.size)
        val listener = listeners.single()
        assertEquals(
            "github.ponyhuang.gimi.data.agent.tools.system.MediaNotificationListenerService",
            listener.androidAttribute("name"),
        )
        assertEquals("true", listener.androidAttribute("exported"))
        val actions = listener.getElementsByTagName("action")
        assertEquals(1, actions.length)
        assertEquals(
            "android.service.notification.NotificationListenerService",
            (actions.item(0) as org.w3c.dom.Element).androidAttribute("name"),
        )
    }

    @Test
    fun sourceManifestOmitsLegacyBackgroundVoiceComponentsAndPermissions() {
        val manifest = sourceXml("src/main/AndroidManifest.xml")
        val permissions = manifest.elements("uses-permission").map { it.androidAttribute("name") }
        assertEquals(
            emptySet<String>(),
            permissions.toSet().intersect(
                setOf(
                    "android.permission.SYSTEM_ALERT_WINDOW",
                    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
                    "android.permission.MODIFY_AUDIO_SETTINGS",
                ),
            ),
        )
        val components = (manifest.elements("service") + manifest.elements("activity"))
            .map { it.androidAttribute("name").substringAfterLast('.') }
        assertFalse("BluetoothVoiceService" in components)
        assertFalse("AssistantLockScreenActivity" in components)
    }

    @Test
    fun sourceManifestDoesNotRegisterDigitalAssistantServices() {
        val manifest = sourceXml("src/main/AndroidManifest.xml")
        val services = manifest.elements("service")
        val names = services.map { it.androidAttribute("name").substringAfterLast('.') }
        assertFalse("AssistantVoiceInteractionService" in names)
        assertFalse("AssistantStubRecognitionService" in names)
        assertFalse(services.any { it.androidAttribute("permission") == "android.permission.BIND_VOICE_INTERACTION" })
        assertFalse(manifest.elements("meta-data").any { it.androidAttribute("name") == "android.voice_interaction" })
        assertFalse(manifest.elements("action").any { it.androidAttribute("name") == "android.service.voice.VoiceInteractionService" })
    }
}
