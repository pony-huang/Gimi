package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Controls how long the device screen stays on before turning off when idle. */
@Singleton
class ScreenTimeoutTool @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver = context.contentResolver

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
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            appliedSeconds * MILLIS_PER_SECOND,
        )
        screenTimeoutState() + mapOf(
            "requestedSeconds" to seconds,
            "appliedSeconds" to appliedSeconds,
        )
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

    private fun writeScreenTimeoutSetting(
        write: () -> Map<String, Any>,
    ): Map<String, Any> {
        if (!Settings.System.canWrite(context)) {
            return screenTimeoutState() + mapOf(
                "success" to false,
                "error" to "WRITE_SETTINGS permission is required. Call open_screen_timeout_permission_settings and enable the permission.",
            )
        }
        return write() + mapOf("success" to true)
    }

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
        const val MINIMUM_TIMEOUT_SECONDS = 10
        const val MAXIMUM_TIMEOUT_SECONDS = 1800
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}
