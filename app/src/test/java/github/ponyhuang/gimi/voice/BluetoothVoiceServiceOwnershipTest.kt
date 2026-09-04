package github.ponyhuang.gimi.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothVoiceServiceOwnershipTest {

    @Test
    fun `service owns only Android lifecycle and notification integration`() {
        val service = File(
            "src/main/java/github/ponyhuang/gimi/voice/BluetoothVoiceService.kt",
        )

        assertTrue("BluetoothVoiceService.kt should exist", service.isFile)
        val content = service.readText()
        listOf(
            "BluetoothPcmRecorder",
            "VoskWakeWordDetector",
            "SpeechRecognitionRepository",
            "VoiceAgentTaskExecutor",
            "VoiceSpeechPlayer",
            "WakeModelProvider",
            "VoiceCommandCapture",
            "processCommand(",
            "confirmVoiceTool(",
            "recoverFromAudioError(",
        ).forEach { forbiddenSymbol ->
            assertFalse(
                "$forbiddenSymbol belongs to data:voicewake runtime, not the Android Service",
                content.contains(forbiddenSymbol),
            )
        }
        assertFalse(
            "Voice notifications should open the current chat without a dedicated session extra",
            content.contains("EXTRA_VOICE_SESSION_ID"),
        )
        assertTrue(
            "Voice notifications should route directly to the current chat",
            content.contains("setAction(MainActivity.ACTION_OPEN_CURRENT_CHAT)"),
        )
    }
}
