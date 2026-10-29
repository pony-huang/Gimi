package github.ponyhuang.gimi.feature.a2a

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by remote A2A agent settings. */
sealed interface RemoteAgentDestination : NavKey {
    /** Configured remote agent list. */
    @Serializable
    data object AgentList : RemoteAgentDestination

    /**
     * Remote agent editor.
     *
     * @property agentId Existing agent id, or null when creating one.
     */
    @Serializable
    data class Editor(val agentId: String? = null) : RemoteAgentDestination
}

/** Resolves all remote agent subpages and keeps their internal transitions inside the feature. */
@Composable
fun A2aEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    RemoteAgentDestination.AgentList -> {
        RemoteAgentListRoute(
            onBack = onBack,
            onAddAgent = { navigate(RemoteAgentDestination.Editor()) },
            onNavigateToEditor = { navigate(RemoteAgentDestination.Editor(it)) },
        )
        true
    }

    is RemoteAgentDestination.Editor -> {
        RemoteAgentEditorRoute(agentId = destination.agentId, onBack = onBack)
        true
    }

    else -> false
}
