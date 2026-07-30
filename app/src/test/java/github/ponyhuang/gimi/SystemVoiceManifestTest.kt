package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemVoiceManifestTest {

    @Test
    fun appDoesNotRegisterAsDefaultDigitalAssistant() {
        val manifest = File("src/main/AndroidManifest.xml")

        assertTrue("AndroidManifest.xml should exist", manifest.isFile)
        val content = manifest.readText()
        assertFalse(content.contains("AssistantVoiceInteractionService"))
        assertFalse(content.contains("AssistantStubRecognitionService"))
        assertFalse(content.contains("android.permission.BIND_VOICE_INTERACTION"))
        assertFalse(content.contains("android.voice_interaction"))
    }
}
