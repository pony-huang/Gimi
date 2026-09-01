package github.ponyhuang.gimi.feature.memory

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by the memory settings feature. */
sealed interface MemoryDestination : NavKey {
    /** Memory settings destination. */
    @Serializable
    data object Settings : MemoryDestination

    /** Mem0 云端记忆历史目的地。 */
    @Serializable
    data object History : MemoryDestination
}

/** Resolves memory-owned destinations. */
@Composable
fun MemoryEntryProvider(destination: NavKey, onBack: () -> Unit, navigate: (NavKey) -> Unit): Boolean =
    when (destination) {
        MemoryDestination.Settings -> {
            MemorySettingsRoute(onBack = onBack, onNavigateToHistory = { navigate(MemoryDestination.History) })
            true
        }

        MemoryDestination.History -> {
            MemoryHistoryRoute(onBack = onBack)
            true
        }

        else -> false
    }
