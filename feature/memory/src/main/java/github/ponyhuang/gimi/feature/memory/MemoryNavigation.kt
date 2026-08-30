package github.ponyhuang.gimi.feature.memory

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by the memory settings feature. */
sealed interface MemoryDestination : NavKey {
    /** Memory settings destination. */
    @Serializable
    data object Settings : MemoryDestination
}

/** Resolves memory-owned destinations. */
@Composable
fun MemoryEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        MemoryDestination.Settings -> {
            MemorySettingsRoute(onBack = onBack)
            true
        }

        else -> false
    }
