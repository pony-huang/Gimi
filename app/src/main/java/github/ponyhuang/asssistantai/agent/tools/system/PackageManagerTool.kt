package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Provides access to installed apps that expose a launcher activity. */
@Singleton
class PackageManagerTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val packageManager: PackageManager
        get() = context.packageManager

    @Tool(
        name = "list_installed_apps",
        description = "Lists currently installed, launchable apps. Each result contains only its display name and package name.",
    )
    fun listInstalledApps(): Map<String, Any> = appListResult(launchableApps())

    @Tool(
        name = "search_installed_apps",
        description = "Searches currently installed, launchable apps by display name or package name. Each result contains only its display name and package name.",
    )
    fun searchInstalledApps(
        @Param("A full or partial app display name or package name to search for.")
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
        description = "Returns whether a currently installed, launchable app with the supplied package name is available.",
    )
    fun isAppInstalled(
        @Param("The Android package name to check, for example com.example.app.")
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
        description = "Opens the launch activity for an installed app. Supply its exact Android package name, obtained from list_installed_apps or search_installed_apps.",
    )
    fun openApp(
        @Param("The exact Android package name of the app to open.")
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
