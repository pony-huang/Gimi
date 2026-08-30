package github.ponyhuang.gimi.feature.toolauthorization

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by tool authorization settings. */
sealed interface ToolAuthorizationDestination : NavKey {
    /** Tool authorization overview. */
    @Serializable
    data object Settings : ToolAuthorizationDestination

    /** Per-tool authorization configuration. */
    @Serializable
    data object Configuration : ToolAuthorizationDestination
}

/** Resolves tool-authorization destinations and owns their internal transition. */
@Composable
fun ToolAuthorizationEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    ToolAuthorizationDestination.Settings -> {
        ToolAuthorizationRoute(
            onBack = onBack,
            onNavigateToConfiguration = {
                navigate(ToolAuthorizationDestination.Configuration)
            },
        )
        true
    }

    ToolAuthorizationDestination.Configuration -> {
        ToolAuthorizationConfigurationRoute(onBack = onBack)
        true
    }

    else -> false
}
