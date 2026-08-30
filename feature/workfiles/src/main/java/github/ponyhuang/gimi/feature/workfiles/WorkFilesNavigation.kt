package github.ponyhuang.gimi.feature.workfiles

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by work-file settings. */
sealed interface WorkFilesDestination : NavKey {
    /** Work-file settings destination. */
    @Serializable
    data object Settings : WorkFilesDestination
}

/** Resolves work-file-owned destinations. */
@Composable
fun WorkFilesEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        WorkFilesDestination.Settings -> {
            WorkFilesSettingsRoute(onBack = onBack)
            true
        }

        else -> false
    }
