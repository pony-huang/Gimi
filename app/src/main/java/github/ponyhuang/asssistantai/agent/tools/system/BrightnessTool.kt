package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

/** Controls the device's system screen brightness and automatic-brightness setting. */
@Singleton
class BrightnessTool @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver = context.contentResolver

    @Tool(name = "get_screen_brightness", description = "Gets the current screen brightness level, automatic-brightness setting, and whether the WRITE_SETTINGS permission is granted.")
    fun getScreenBrightness(): Map<String, Any> = brightnessState()

    @Tool(
        name = "set_screen_brightness",
        description = "Sets the screen brightness to an absolute level from 1 (darkest) to 255 (brightest). Disables automatic brightness. Requires the WRITE_SETTINGS permission; otherwise the request fails with a hint to grant it first.",
    )
    fun setScreenBrightness(
        @Param("Target screen brightness from 1 (darkest) to 255 (brightest). This disables automatic brightness.")
        level: Int,
    ): Map<String, Any> = writeBrightnessSetting {
        val appliedLevel = level.coerceIn(MINIMUM_BRIGHTNESS, MAXIMUM_BRIGHTNESS)
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, appliedLevel)
        brightnessState() + mapOf(
            "requestedLevel" to level,
            "appliedLevel" to appliedLevel,
        )
    }

    @Tool(
        name = "set_automatic_brightness",
        description = "Enables or disables automatic screen brightness. Requires the WRITE_SETTINGS permission; otherwise the request fails with a hint to grant it first.",
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
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        brightnessState()
    }

    @Tool(
        name = "open_brightness_permission_settings",
        description = "Opens the system screen that lets the user grant the WRITE_SETTINGS permission required to change screen brightness. Returns the current permission state.",
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

    private fun writeBrightnessSetting(
        write: () -> Map<String, Any>,
    ): Map<String, Any> {
        if (!Settings.System.canWrite(context)) {
            return brightnessState() + mapOf(
                "success" to false,
                "error" to "WRITE_SETTINGS permission is required. Call openBrightnessPermissionSettings and enable the permission.",
            )
        }
        return write() + mapOf("success" to true)
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

    private companion object {
        const val MINIMUM_BRIGHTNESS = 1
        const val MAXIMUM_BRIGHTNESS = 255
        const val DEFAULT_BRIGHTNESS = 128
    }
}
