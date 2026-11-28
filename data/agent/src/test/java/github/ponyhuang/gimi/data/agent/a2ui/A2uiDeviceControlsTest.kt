package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.data.agent.tools.system.AudioTool
import github.ponyhuang.gimi.data.agent.tools.system.FlashlightTool
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiDeviceControlsTest {
    private val audio = mockk<AudioTool>()
    private val torch = mockk<FlashlightTool>()
    private val authorization = mockk<ToolAuthorizationRepository> {
        every { revision } returns MutableStateFlow(0L)
    }
    private val controls = AndroidA2uiDeviceControls(audio, torch, authorization)

    @Test
    fun disabledToolsCannotBeInvokedByAControl() = runTest {
        every { authorization.enabledToolIds() } returns emptySet()

        assertFalse(controls.setVolume(5))
        assertFalse(controls.setFlashlight(true))
        assertEquals(A2uiFlashlightState.Disabled, controls.observeFlashlight().first())
        verify(exactly = 0) { audio.setVolumeFromControl(any()) }
        verify(exactly = 0) { torch.setFlashlight(any()) }
    }

    @Test
    fun flashlightReportsOnlyConfirmedMode() = runTest {
        every { authorization.enabledToolIds() } returns setOf("set_flashlight")
        every { torch.setFlashlight(true) } returns mapOf("success" to true)
        every { torch.observeTorch() } returns flowOf(false, true)

        assertTrue(controls.setFlashlight(true))

        every { torch.observeTorch() } returns flowOf(false)
        assertFalse(controls.setFlashlight(true))
    }

    @Test
    fun missingCameraPermissionLeavesTheTorchUnobserved() = runTest {
        every { authorization.enabledToolIds() } returns setOf("set_flashlight", "request_flashlight_permission")
        every { torch.hasCameraPermission() } returns false

        assertEquals(A2uiFlashlightState.PermissionRequired, controls.observeFlashlight().first())
        verify(exactly = 0) { torch.observeTorch() }
    }

    @Test
    fun readOnlyVolumeRemainsVisibleAndRejectsWrites() = runTest {
        every { authorization.enabledToolIds() } returns setOf("get_media_volume")
        val volume = A2uiMediaVolume(5, 0, 15, false)
        every { audio.readVolumeForControl(false) } returns volume

        assertEquals(volume, controls.observeVolume().first())
        assertFalse(controls.setVolume(8))
        verify(exactly = 0) { audio.setVolumeFromControl(any()) }
    }
}
