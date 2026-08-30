package github.ponyhuang.gimi.feature.plugin

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by plugin settings. */
sealed interface PluginDestination : NavKey {
    /** Installed plugin list. */
    @Serializable
    data object Settings : PluginDestination

    /**
     * Configuration UI for one plugin.
     *
     * @property pluginId Stable plugin identifier.
     */
    @Serializable
    data class Configuration(val pluginId: String) : PluginDestination
}

/** Resolves plugin destinations and owns the list-to-configuration transition. */
@Composable
fun PluginEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    PluginDestination.Settings -> {
        PluginSettingsRoute(
            onBack = onBack,
            onNavigateToConfig = { navigate(PluginDestination.Configuration(it)) },
        )
        true
    }

    is PluginDestination.Configuration -> {
        PluginConfigRoute(
            pluginId = destination.pluginId,
            onBack = onBack,
        )
        true
    }

    else -> false
}
