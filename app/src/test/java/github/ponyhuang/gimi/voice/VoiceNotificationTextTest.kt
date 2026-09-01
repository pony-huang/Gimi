package github.ponyhuang.gimi.voice

import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceStatus
import github.ponyhuang.gimi.data.voicewake.VoicePipelineEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNotificationTextTest {
    @Test
    fun knownBluetoothDeviceReplacesTransientCaptureMessage() {
        val text = VoiceNotificationText.forEvent(
            VoicePipelineEvent(
                status = BluetoothVoiceStatus.CapturingCommand,
                message = "请说出指令",
                deviceName = "Buds Pro",
            ),
        )

        assertEquals("Buds Pro", text)
    }
}
