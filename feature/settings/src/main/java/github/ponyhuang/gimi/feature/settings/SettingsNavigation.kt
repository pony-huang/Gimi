package github.ponyhuang.gimi.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by the application settings feature. */
sealed interface SettingsDestination : NavKey {
    /** Application settings destination. */
    @Serializable
    data object Settings : SettingsDestination
}

/** Cross-capability navigation callbacks displayed by the settings hub. */
class SettingsNavigationCallbacks(
    val onBack: () -> Unit,
    val onNavigateToModelService: () -> Unit,
    val onNavigateToDefaultModels: () -> Unit,
    val onNavigateToVoiceWake: () -> Unit,
    val onNavigateToMcpServers: () -> Unit,
    val onNavigateToPlugins: () -> Unit,
    val onNavigateToSkills: () -> Unit,
    val onNavigateToWorkFiles: () -> Unit,
    val onNavigateToPermissions: () -> Unit,
    val onNavigateToToolAuthorization: () -> Unit,
    val onNavigateToRecommendations: () -> Unit,
    val onNavigateToMemory: () -> Unit,
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
            onNavigateToVoiceWake = callbacks.onNavigateToVoiceWake,
            onNavigateToMcpServers = callbacks.onNavigateToMcpServers,
            onNavigateToPlugins = callbacks.onNavigateToPlugins,
            onNavigateToSkills = callbacks.onNavigateToSkills,
            onNavigateToWorkFiles = callbacks.onNavigateToWorkFiles,
            onNavigateToPermissions = callbacks.onNavigateToPermissions,
            onNavigateToToolAuthorization = callbacks.onNavigateToToolAuthorization,
            onNavigateToRecommendations = callbacks.onNavigateToRecommendations,
            onNavigateToMemory = callbacks.onNavigateToMemory,
        )
        true
    }

    else -> false
}
