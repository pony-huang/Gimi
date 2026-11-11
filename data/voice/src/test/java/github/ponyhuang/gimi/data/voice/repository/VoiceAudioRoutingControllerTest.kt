package github.ponyhuang.gimi.data.voice.repository

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioRouting
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 覆盖系统设备列表、用户偏好与会话清理的 Android 边界。 */
class VoiceAudioRoutingControllerTest {
    private val audio = mockk<AudioManager>(relaxed = true)
    private val handler = mockk<Handler>(relaxed = true)
    private val speaker = device(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    private val headset = device(2, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    private val callback = slot<AudioDeviceCallback>()
    private var devices = listOf(speaker, headset)
    private var current: AudioDeviceInfo? = speaker
    private var state = VoiceAudioRouting()
    private lateinit var controller: VoiceAudioRoutingController

    @Before
    fun setup() {
        every { audio.availableCommunicationDevices } answers { devices }
        every { audio.communicationDevice } answers { current }
        every { audio.registerAudioDeviceCallback(capture(callback), handler) } just Runs
        every { audio.setCommunicationDevice(any()) } answers { current = firstArg(); true }
        controller = VoiceAudioRoutingController(audio, { state = it }, handler)
    }

    @Test
    fun manuallySelectedSpeakerIsExplicitlyRequestedAndSurvivesConnections() {
        controller.start()
        controller.select(1)
        callback.captured.onAudioDevicesAdded(arrayOf(headset))
        assertEquals(1, state.preferredDeviceId)
        assertEquals(1, state.currentDeviceId)
        verify { audio.setCommunicationDevice(speaker) }
    }

    @Test
    fun disconnectResetsManualPreferenceAndNotifiesWithActualFallback() {
        controller.start()
        controller.select(2)
        devices = listOf(speaker)
        callback.captured.onAudioDevicesRemoved(arrayOf(headset))
        assertNull(state.preferredDeviceId)
        assertEquals(1, state.currentDeviceId)
        assertEquals(1L, state.noticeRevision)
        assertFalse(state.selectionFailed)
    }

    @Test
    fun rejectedSelectionKeepsActualDeviceAndReportsFailure() {
        devices = listOf(speaker)
        controller.start()
        devices = listOf(speaker, headset)
        every { audio.setCommunicationDevice(headset) } returns false
        callback.captured.onAudioDevicesAdded(arrayOf(headset))
        assertEquals(1, state.currentDeviceId)
        assertTrue(state.selectionFailed)
        assertFalse(state.switching)
    }

    @Test
    fun closeUnregistersAndRestoresPreviousDeviceAndIgnoresLateCallbacks() {
        controller.start()
        controller.close()
        val closedState = state
        callback.captured.onAudioDevicesRemoved(arrayOf(headset))
        assertEquals(closedState, state)
        verify { audio.unregisterAudioDeviceCallback(callback.captured) }
        verify { audio.removeOnCommunicationDeviceChangedListener(any()) }
        verify { audio.clearCommunicationDevice() }
        assertEquals(speaker, current)
    }

    private fun device(id: Int, type: Int): AudioDeviceInfo = mockk {
        every { getId() } returns id
        every { getType() } returns type
        every { productName } returns "device-$id"
    }
}
