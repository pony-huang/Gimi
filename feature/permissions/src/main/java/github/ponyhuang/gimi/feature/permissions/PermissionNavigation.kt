package github.ponyhuang.gimi.feature.permissions

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by the permission settings feature. */
sealed interface PermissionDestination : NavKey {
    /** Permission settings destination. */
    @Serializable
    data object Settings : PermissionDestination
}

/** Resolves permission-owned destinations. */
@Composable
fun PermissionEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        PermissionDestination.Settings -> {
            PermissionSettingsRoute(onBack = onBack)
            true
        }

        else -> false
    }
