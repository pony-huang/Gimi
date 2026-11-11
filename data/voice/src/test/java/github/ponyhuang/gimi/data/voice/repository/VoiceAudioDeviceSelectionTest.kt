package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 保留路由优先级回归覆盖，避免连接耳机重新覆盖用户的手动选择。 */
class VoiceAudioDeviceSelectionTest {
    private val speaker = VoiceAudioDevice(1, VoiceAudioDeviceKind.Speaker, "speaker")
    private val bluetooth = VoiceAudioDevice(2, VoiceAudioDeviceKind.Bluetooth, "bluetooth")
    private val usb = VoiceAudioDevice(3, VoiceAudioDeviceKind.Usb, "usb")

    @Test
    fun automaticUsesConnectionOrderRatherThanPlatformListOrder() {
        assertEquals(bluetooth, selectVoiceAudioDevice(listOf(bluetooth, speaker, usb), listOf(1, 3, 2), null))
    }

    @Test
    fun manualSpeakerSurvivesNewHeadsetConnection() {
        assertEquals(speaker, selectVoiceAudioDevice(listOf(speaker, bluetooth, usb), listOf(1, 2, 3), 1))
    }

    @Test
    fun disconnectedManualDeviceFallsBackToRemainingHeadsetThenSpeaker() {
        assertEquals(bluetooth, selectVoiceAudioDevice(listOf(speaker, bluetooth), listOf(1, 2), 3))
        assertEquals(speaker, selectVoiceAudioDevice(listOf(speaker), listOf(1), 3))
    }

    @Test
    fun noAvailableDeviceDoesNotInventSelection() {
        assertNull(selectVoiceAudioDevice(emptyList(), emptyList(), null))
    }
}
