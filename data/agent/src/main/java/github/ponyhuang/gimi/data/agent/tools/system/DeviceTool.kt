package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备域工具：屏幕亮度、自动亮度、屏幕超时。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.DEVICE]。
 */
@Singleton
class DeviceTool @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver = context.contentResolver

    // ---------- 屏幕亮度 ----------

    @Tool(name = "get_screen_brightness", description = "Returns the current brightness level, auto-brightness setting, and whether permission to change brightness is granted.")
    fun getScreenBrightness(): Map<String, Any> = brightnessState()

    @Tool(
        name = "set_screen_brightness",
        description = "Sets brightness to an absolute level and disables auto-brightness. Requires permission to change system settings.",
        requireConfirmation = true,
    )
    fun setScreenBrightness(
        @Param("Target brightness level from 0 to 255 (darkest to brightest).")
        level: Int,
    ): Map<String, Any> = writeBrightnessSetting {
        val appliedLevel = level.coerceIn(MINIMUM_BRIGHTNESS, MAXIMUM_BRIGHTNESS)
        val modeWritten = Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val levelWritten = Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            appliedLevel,
        )
        val result = brightnessState() + mapOf(
            "requestedLevel" to level,
            "appliedLevel" to appliedLevel,
        )
        if (allSettingsWritten(modeWritten, levelWritten)) {
            result
        } else {
            result + mapOf("success" to false, "error" to "The system rejected the brightness update.")
        }
    }

    @Tool(
        name = "set_automatic_brightness",
        description = "Enables or disables auto-brightness. Requires permission to change system settings.",
        requireConfirmation = true,
    )
    fun setAutomaticBrightness(
        @Param("Whether automatic screen brightness should be enabled.")
        enabled: Boolean,
    ): Map<String, Any> = writeBrightnessSetting {
        val mode = if (enabled) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        if (Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)) {
            brightnessState()
        } else {
            brightnessState() + mapOf(
                "success" to false,
                "error" to "The system rejected the automatic brightness update.",
            )
        }
    }

    @Tool(
        name = "open_brightness_permission_settings",
        description = "Opens system settings so the user can grant permission to change brightness.",
    )
    fun openBrightnessPermissionSettings(): Map<String, Any> {
        if (Settings.System.canWrite(context)) {
            return brightnessState() + mapOf("permissionSettingsOpened" to false)
        }
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return brightnessState() + mapOf("permissionSettingsOpened" to true)
    }

    // ---------- 屏幕超时 ----------

    @Tool(
        name = "get_screen_timeout",
        description = "Returns the current screen timeout in seconds, the supported range, and whether permission to change it is granted.",
    )
    fun getScreenTimeout(): Map<String, Any> = screenTimeoutState()

    @Tool(
        name = "set_screen_timeout",
        description = "Sets the screen timeout in seconds. Requires permission to change system settings.",
        requireConfirmation = true,
    )
    fun setScreenTimeout(
        @Param("Target screen timeout in seconds.")
        seconds: Int,
    ): Map<String, Any> = writeScreenTimeoutSetting {
        val appliedSeconds = seconds.coerceIn(MINIMUM_TIMEOUT_SECONDS, MAXIMUM_TIMEOUT_SECONDS)
        val written = Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            appliedSeconds * MILLIS_PER_SECOND,
        )
        val result = screenTimeoutState() + mapOf(
            "requestedSeconds" to seconds,
            "appliedSeconds" to appliedSeconds,
        )
        if (written) {
            result
        } else {
            result + mapOf("success" to false, "error" to "The system rejected the timeout update.")
        }
    }

    @Tool(
        name = "open_screen_timeout_permission_settings",
        description = "Opens system settings so the user can grant permission to change the screen timeout.",
    )
    fun openScreenTimeoutPermissionSettings(): Map<String, Any> {
        if (Settings.System.canWrite(context)) {
            return screenTimeoutState() + mapOf("permissionSettingsOpened" to false)
        }
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return screenTimeoutState() + mapOf("permissionSettingsOpened" to true)
    }

    // ---------- helpers ----------

    private fun writeBrightnessSetting(
        write: () -> Map<String, Any>,
    ): Map<String, Any> {
        if (!Settings.System.canWrite(context)) {
            return brightnessState() + mapOf(
                "success" to false,
                "error" to "WRITE_SETTINGS permission is required. Call openBrightnessPermissionSettings and enable the permission.",
            )
        }
        val result = write()
        return if ("success" in result) result else result + mapOf("success" to true)
    }

    private fun writeScreenTimeoutSetting(
        write: () -> Map<String, Any>,
    ): Map<String, Any> {
        if (!Settings.System.canWrite(context)) {
            return screenTimeoutState() + mapOf(
                "success" to false,
                "error" to "WRITE_SETTINGS permission is required. Call open_screen_timeout_permission_settings and enable the permission.",
            )
        }
        val result = write()
        return if ("success" in result) result else result + mapOf("success" to true)
    }

    private fun brightnessState(): Map<String, Any> = mapOf(
        "brightnessLevel" to Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            DEFAULT_BRIGHTNESS,
        ),
        "automaticBrightnessEnabled" to (
            Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ),
        "canWriteSystemSettings" to Settings.System.canWrite(context),
        "minimumBrightnessLevel" to MINIMUM_BRIGHTNESS,
        "maximumBrightnessLevel" to MAXIMUM_BRIGHTNESS,
    )

    private fun screenTimeoutState(): Map<String, Any> = mapOf(
        "timeoutSeconds" to currentTimeoutSeconds(),
        "minimumTimeoutSeconds" to MINIMUM_TIMEOUT_SECONDS,
        "maximumTimeoutSeconds" to MAXIMUM_TIMEOUT_SECONDS,
        "canWriteSystemSettings" to Settings.System.canWrite(context),
    )

    private fun currentTimeoutSeconds(): Int {
        val rawMillis = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            DEFAULT_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
        )
        // SCREEN_OFF_TIMEOUT is in milliseconds; the value can be negative on some OEMs to mean "never".
        if (rawMillis <= 0) return rawMillis
        return rawMillis / MILLIS_PER_SECOND
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000
        const val MINIMUM_BRIGHTNESS = 1
        const val MAXIMUM_BRIGHTNESS = 255
        const val DEFAULT_BRIGHTNESS = 128
        const val MINIMUM_TIMEOUT_SECONDS = 10
        const val MAXIMUM_TIMEOUT_SECONDS = 1800
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}

internal fun allSettingsWritten(vararg results: Boolean): Boolean = results.all { it }
