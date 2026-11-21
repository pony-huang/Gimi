package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 保留自动路由优先级回归覆盖：按连接顺序选耳机，最后回退扬声器。 */
class VoiceAudioDeviceSelectionTest {
    private val speaker = VoiceAudioDevice(1, VoiceAudioDeviceKind.Speaker, "speaker")
    private val bluetooth = VoiceAudioDevice(2, VoiceAudioDeviceKind.Bluetooth, "bluetooth")
    private val usb = VoiceAudioDevice(3, VoiceAudioDeviceKind.Usb, "usb")

    @Test
    fun automaticUsesConnectionOrderRatherThanPlatformListOrder() {
        assertEquals(bluetooth, selectVoiceAudioDevice(listOf(bluetooth, speaker, usb), listOf(1, 3, 2)))
    }

    @Test
    fun connectedSpeakerWinsWhenNoHeadset() {
        assertEquals(speaker, selectVoiceAudioDevice(listOf(speaker, bluetooth), listOf(1)))
    }

    @Test
    fun noAvailableDeviceDoesNotInventSelection() {
        assertNull(selectVoiceAudioDevice(emptyList(), emptyList()))
    }
}
