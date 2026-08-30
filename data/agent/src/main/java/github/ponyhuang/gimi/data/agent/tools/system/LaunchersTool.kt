package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用启动域工具：系统相机拍照 / 录像、安装应用列表 / 搜索 / 打开。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.LAUNCHERS]。
 */
@Singleton
class LaunchersTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val packageManager: PackageManager
        get() = context.packageManager

    // ---------- 相机 ----------

    @Tool(name = "capture_photo", description = "Opens the system camera to take a photo.", requireConfirmation = true)
    fun capturePhoto(): Map<String, Any> = queue.request(
        title = "Take photo",
        summary = "Open the system camera to take a photo.",
        intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )

    @Tool(name = "capture_video", description = "Opens the system camera to record a video.", requireConfirmation = true)
    fun captureVideo(): Map<String, Any> = queue.request(
        title = "Record video",
        summary = "Open the system camera to record a video.",
        intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE),
    )

    // ---------- 应用列表 ----------

    @Tool(
        name = "list_installed_apps",
        description = "Lists apps currently installed on the device, returning each app's display name and ID.",
    )
    fun listInstalledApps(): Map<String, Any> = appListResult(launchableApps())

    @Tool(
        name = "search_installed_apps",
        description = "Searches installed apps by display name or ID.",
    )
    fun searchInstalledApps(
        @Param("A full or partial app display name or ID to search for.")
        query: String,
    ): Map<String, Any> {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "query must not be blank." }
        val apps = launchableApps().filter { app ->
            app.name.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true)
        }
        return appListResult(apps)
    }

    @Tool(
        name = "is_app_installed",
        description = "Returns whether an installed app with the supplied ID is available.",
    )
    fun isAppInstalled(
        @Param("The app ID to check, for example com.example.app.")
        packageName: String,
    ): Map<String, Any> {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) { "packageName must not be blank." }
        val app = launchableApps().firstOrNull { it.packageName == normalizedPackageName }
        return mapOf(
            "success" to true,
            "packageName" to normalizedPackageName,
            "installed" to (app != null),
        ) + if (app != null) mapOf("name" to app.name) else emptyMap()
    }

    @Tool(
        name = "open_app",
        description = "Opens an installed app. Supply its exact ID, obtained from list_installed_apps or search_installed_apps.",
    )
    fun openApp(
        @Param("The exact app ID of the app to open.")
        packageName: String,
    ): Map<String, Any> {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) { "packageName must not be blank." }
        val app = launchableApps().firstOrNull { it.packageName == normalizedPackageName }
            ?: return mapOf(
                "success" to false,
                "error" to "No installed launchable app was found for package $normalizedPackageName.",
            )
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return mapOf(
                "success" to false,
                "error" to "The app ${app.name} has no launch activity.",
            )
        return queue.request(
            "Open ${app.name}",
            "Open ${app.name} (${app.packageName}).",
            launchIntent,
        )
    }

    // ---------- helpers ----------

    private fun launchableApps(): List<App> = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        PackageManager.ResolveInfoFlags.of(0),
    ).map { resolveInfo ->
        val activityInfo = resolveInfo.activityInfo
        App(
            name = resolveInfo.loadLabel(packageManager).toString(),
            packageName = activityInfo.packageName,
        )
    }.distinctBy(App::packageName).sortedWith(
        compareBy<App, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.packageName },
    )

    private fun appListResult(apps: List<App>): Map<String, Any> = mapOf(
        "success" to true,
        "apps" to apps.map { app ->
            mapOf(
                "name" to app.name,
                "packageName" to app.packageName,
            )
        },
    )

    private data class App(
        val name: String,
        val packageName: String,
    )
}
