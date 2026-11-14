package github.ponyhuang.gimi.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.feature.settings.about.AboutRoute
import github.ponyhuang.gimi.feature.settings.about.PrivacyPolicyRoute
import kotlinx.serialization.Serializable

/** Destinations owned by the application settings feature. */
sealed interface SettingsDestination : NavKey {
    /** Application settings destination. */
    @Serializable
    data object Settings : SettingsDestination

    /** 「关于」页：内含检查更新、项目主页等应用信息。 */
    @Serializable
    data object About : SettingsDestination

    /** 隐私政策页：内嵌 WebView 打开仓库 wiki 页面。 */
    @Serializable
    data object PrivacyPolicy : SettingsDestination
}

/** Cross-capability navigation callbacks displayed by the settings hub. */
class SettingsNavigationCallbacks(
    val onBack: () -> Unit,
    val onNavigateToModelService: () -> Unit,
    val onNavigateToDefaultModels: () -> Unit,
    val onNavigateToMcpServers: () -> Unit,
    val onNavigateToRemoteAgents: () -> Unit,
    val onNavigateToAppFunctions: () -> Unit,
    val onNavigateToSandbox: () -> Unit,
    val onNavigateToMobileUse: () -> Unit,
    val onNavigateToPlugins: () -> Unit,
    val onNavigateToSkills: () -> Unit,
    val onNavigateToWorkspace: () -> Unit,
    val onNavigateToPermissions: () -> Unit,
    val onNavigateToRecommendations: () -> Unit,
    val onNavigateToMemory: () -> Unit,
    val onNavigateToAbout: () -> Unit,
    val onNavigateToPrivacyPolicy: () -> Unit,
)

/** Resolves the settings hub while the app supplies only cross-feature callbacks. */
@Composable
fun SettingsEntryProvider(
    destination: NavKey,
    appVersionName: String,
    callbacks: SettingsNavigationCallbacks,
): Boolean = when (destination) {
    SettingsDestination.Settings -> {
        SettingsRoute(
            appVersionName = appVersionName,
            onBack = callbacks.onBack,
            onNavigateToModelService = callbacks.onNavigateToModelService,
            onNavigateToDefaultModels = callbacks.onNavigateToDefaultModels,
            onNavigateToMcpServers = callbacks.onNavigateToMcpServers,
            onNavigateToRemoteAgents = callbacks.onNavigateToRemoteAgents,
            onNavigateToAppFunctions = callbacks.onNavigateToAppFunctions,
            onNavigateToSandbox = callbacks.onNavigateToSandbox,
            onNavigateToMobileUse = callbacks.onNavigateToMobileUse,
            onNavigateToPlugins = callbacks.onNavigateToPlugins,
            onNavigateToSkills = callbacks.onNavigateToSkills,
            onNavigateToWorkspace = callbacks.onNavigateToWorkspace,
            onNavigateToPermissions = callbacks.onNavigateToPermissions,
            onNavigateToRecommendations = callbacks.onNavigateToRecommendations,
            onNavigateToMemory = callbacks.onNavigateToMemory,
            onNavigateToAbout = callbacks.onNavigateToAbout,
        )
        true
    }

    SettingsDestination.About -> {
        AboutRoute(
            appVersionName = appVersionName,
            onBack = callbacks.onBack,
            onOpenPrivacyPolicy = callbacks.onNavigateToPrivacyPolicy,
        )
        true
    }

    SettingsDestination.PrivacyPolicy -> {
        PrivacyPolicyRoute(onBack = callbacks.onBack)
        true
    }

    else -> false
}
