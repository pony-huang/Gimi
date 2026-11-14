package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by recommendation settings. */
sealed interface RecommendationDestination : NavKey {
    /** Recommendation settings destination. */
    @Serializable
    data object Settings : RecommendationDestination

    /** 「推荐更新」独立页：承载推荐开关、更新间隔与手动更新。 */
    @Serializable
    data object UpdateSettings : RecommendationDestination
}

/** Resolves recommendation-owned destinations and delegates cross-feature navigation. */
@Composable
fun RecommendationEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
    onOpenFastModelSettings: () -> Unit,
    onOpenSimpleChatPromptSettings: () -> Unit = {},
): Boolean = when (destination) {
    RecommendationDestination.Settings -> {
        RecommendationSettingsRoute(
            onBack = onBack,
            onOpenSimpleChatPromptSettings = onOpenSimpleChatPromptSettings,
            onOpenRecommendationUpdate = { navigate(RecommendationDestination.UpdateSettings) },
        )
        true
    }

    RecommendationDestination.UpdateSettings -> {
        RecommendationUpdateSettingsRoute(
            onBack = onBack,
            onOpenFastModelSettings = onOpenFastModelSettings,
        )
        true
    }

    else -> false
}
