package github.ponyhuang.gimi.feature.workspace

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by the workspace management feature. */
sealed interface WorkspaceDestination : NavKey {
    /** Workspace management destination. */
    @Serializable
    data object Manager : WorkspaceDestination
}

/** Resolves workspace-owned destinations. */
@Composable
fun WorkspaceEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        WorkspaceDestination.Manager -> {
            WorkspaceRoute(onBack = onBack)
            true
        }

        else -> false
    }
