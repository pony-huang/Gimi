package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by recommendation settings. */
sealed interface RecommendationDestination : NavKey {
    /** Recommendation settings destination. */
    @Serializable
    data object Settings : RecommendationDestination
}

/** Resolves recommendation-owned destinations and delegates cross-feature navigation. */
@Composable
fun RecommendationEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
): Boolean = when (destination) {
    RecommendationDestination.Settings -> {
        RecommendationSettingsRoute(
            onBack = onBack,
            onOpenPermissions = onOpenPermissions,
        )
        true
    }

    else -> false
}
