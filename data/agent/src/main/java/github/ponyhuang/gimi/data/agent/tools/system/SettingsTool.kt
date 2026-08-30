package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Intent
import android.provider.Settings
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统设置导航工具：跳转到各类系统设置页。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.SETTINGS]。
 */
@Singleton
class SettingsTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "open_settings", description = "Opens the requested system settings page.")
    fun openSettings(@Param("Settings page: general, wireless, airplane, wifi, bluetooth, date, locale, input, display, security, location, storage.") page: String): Map<String, Any> {
        val normalized = page.trim().lowercase()
        val action = when (normalized) {
            "general" -> Settings.ACTION_SETTINGS
            "wireless" -> Settings.ACTION_WIRELESS_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "locale" -> Settings.ACTION_LOCALE_SETTINGS
            "input" -> Settings.ACTION_INPUT_METHOD_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else -> return mapOf("success" to false, "error" to "Unsupported settings page.")
        }
        return queue.request("Open settings", "Open the $normalized settings page.", Intent(action))
    }
}
