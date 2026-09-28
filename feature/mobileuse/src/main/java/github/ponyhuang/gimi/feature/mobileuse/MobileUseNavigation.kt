package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 后台静默操作设置页。 */
sealed interface MobileUseDestination : NavKey {
    @Serializable
    data object Settings : MobileUseDestination
}

@Composable
fun MobileUseEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        MobileUseDestination.Settings -> {
            MobileUseRoute(onBack)
            true
        }
        else -> false
    }
