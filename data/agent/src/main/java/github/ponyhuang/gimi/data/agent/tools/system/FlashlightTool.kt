package github.ponyhuang.gimi.data.agent.tools.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.agent.permission.FlashlightPermissionActivity
import javax.inject.Inject
import javax.inject.Singleton

/** 使用系统相机服务开关手电筒，不打开相机或采集图像。 */
@Singleton
class FlashlightTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    @Tool(
        name = "set_flashlight",
        description = "Turns the device flashlight on or off. Requires camera permission. Does not take photos. Returns whether the system accepted the request, not a measured torch state.",
        requireConfirmation = true,
    )
    fun setFlashlight(
        @Param("True to turn the flashlight on; false to turn it off.")
        enabled: Boolean,
    ): Map<String, Any> {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return failure("Camera permission is required. Call request_flashlight_permission, grant access, then retry.")
        }
        val manager = context.getSystemService(CameraManager::class.java)
            ?: return failure("Camera service is unavailable on this device.")
        return try {
            val cameras = manager.cameraIdList.map { id -> id to manager.getCameraCharacteristics(id) }
                .filter { (_, characteristics) -> characteristics[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true }
            // 优先使用后置闪光灯；部分设备仅有其他朝向的闪光灯，仍允许使用。
            val camera = cameras.firstOrNull { (_, characteristics) ->
                characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameras.firstOrNull()
            if (camera == null) return failure("No flashlight is available on this device.")
            manager.setTorchMode(camera.first, enabled)
            mapOf("success" to true, "requestedEnabled" to enabled)
        } catch (error: CameraAccessException) {
            failure("Flashlight is unavailable; the camera may be busy or disabled. ${error.message.orEmpty()}")
        } catch (error: SecurityException) {
            failure("Camera access was denied. Grant camera permission and retry.")
        } catch (error: IllegalArgumentException) {
            failure("The selected flashlight is no longer available.")
        }
    }

    @Tool(
        name = "request_flashlight_permission",
        description = "Prompts the user to grant camera permission required to control the flashlight. Retry set_flashlight after permission is granted.",
    )
    fun requestFlashlightPermission(): Map<String, Any> {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return mapOf("success" to true, "permissionGranted" to true)
        }
        return queue.request(
            "Grant flashlight access",
            "Allow camera permission to control the flashlight without taking photos.",
            Intent(context, FlashlightPermissionActivity::class.java),
        )
    }

    private fun failure(message: String): Map<String, Any> = mapOf("success" to false, "error" to message)
}
