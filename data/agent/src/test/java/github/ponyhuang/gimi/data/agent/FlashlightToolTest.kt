package github.ponyhuang.gimi.data.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import github.ponyhuang.gimi.data.agent.tools.system.FlashlightTool
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class FlashlightToolTest {
    private val context = mockk<Context>()
    private val manager = mockk<CameraManager>(relaxed = true)
    private val tool = FlashlightTool(context, mockk(relaxed = true))

    init {
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { context.getSystemService(CameraManager::class.java) } returns manager
    }

    @Test
    fun missingPermissionDoesNotAccessCamera() {
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_DENIED
        assertEquals(false, tool.setFlashlight(true)["success"])
        verify(exactly = 0) { context.getSystemService(CameraManager::class.java) }
    }

    @Test
    fun grantedPermissionDoesNotPromptAgain() {
        assertEquals(mapOf("success" to true, "permissionGranted" to true), tool.requestFlashlightPermission())
    }

    @Test
    fun noCameraDoesNotAttemptTorchUpdate() {
        every { manager.cameraIdList } returns emptyArray()
        assertEquals(false, tool.setFlashlight(true)["success"])
        verify(exactly = 0) { manager.setTorchMode(any(), any()) }
    }

    @Test
    fun busyCameraReturnsFailure() {
        every { manager.cameraIdList } throws CameraAccessException(CameraAccessException.CAMERA_IN_USE)
        assertEquals(false, tool.setFlashlight(true)["success"])
    }

    @Test
    fun revokedPermissionReturnsFailure() {
        every { manager.cameraIdList } throws SecurityException("revoked")
        assertEquals(false, tool.setFlashlight(false)["success"])
    }

}
