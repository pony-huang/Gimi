package github.ponyhuang.gimi.data.voicewake

import android.media.AudioDeviceInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAudioRouteFallbackTest {
    private val speaker = SpeakerAudioRoute("Phone speaker")
    private val bluetooth = BluetoothAudioRoute(
        input = mockk<AudioDeviceInfo>(relaxed = true),
        communication = mockk<AudioDeviceInfo>(relaxed = true),
        name = "Headset",
    )

    @Test
    fun failedBluetoothActivationFallsBackToSpeaker() {
        val attempts = mutableListOf<String>()

        val active = activatePreferredVoiceRoute(bluetooth, speaker) { route ->
            attempts += route.id
            route is SpeakerAudioRoute
        }

        assertEquals(speaker, active)
        assertEquals(listOf(bluetooth.id, speaker.id), attempts)
    }

    @Test
    fun successfulBluetoothActivationDoesNotTouchSpeaker() {
        val attempts = mutableListOf<String>()

        val active = activatePreferredVoiceRoute(bluetooth, speaker) { route ->
            attempts += route.id
            true
        }

        assertEquals(bluetooth, active)
        assertEquals(listOf(bluetooth.id), attempts)
    }

    @Test
    fun bluetoothPermissionOrRoutingExceptionFallsBackToSpeaker() {
        val attempts = mutableListOf<String>()

        val active = activatePreferredVoiceRoute(bluetooth, speaker) { route ->
            attempts += route.id
            if (route is BluetoothAudioRoute) throw SecurityException("Bluetooth denied")
            true
        }

        assertEquals(speaker, active)
        assertEquals(listOf(bluetooth.id, speaker.id), attempts)
    }
}
