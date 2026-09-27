package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecommendationRefreshActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failureRetryWaitStillAllowsManualRefresh() {
        val actions = mutableListOf<RecommendationSettingsAction>()
        setScreen(
            RecommendationSettingsUiState(
                refreshStatus = RecommendationRefreshStatus.Refreshing,
                lastError = "Configure an OpenAI-compatible fast model.",
                retryDelaySeconds = 30,
            ),
            actions::add,
        )

        composeRule.onNodeWithTag(REFRESH_TAG).assertIsEnabled().performClick()

        assertEquals(listOf(RecommendationSettingsAction.RefreshNow), actions)
    }

    @Test
    fun inFlightModelCallBlocksManualRefresh() {
        setScreen(
            RecommendationSettingsUiState(refreshStatus = RecommendationRefreshStatus.Refreshing),
        )

        composeRule.onNodeWithTag(REFRESH_TAG).assertIsNotEnabled()
    }

    @Test
    fun finalFailureAllowsManualRefresh() {
        setScreen(
            RecommendationSettingsUiState(
                refreshStatus = RecommendationRefreshStatus.Idle,
                lastError = "Recommendation update failed",
            ),
        )

        composeRule.onNodeWithTag(REFRESH_TAG).assertIsEnabled()
    }

    private fun setScreen(
        state: RecommendationSettingsUiState,
        onAction: (RecommendationSettingsAction) -> Unit = {},
    ) {
        composeRule.setContent {
            AsssistantaiTheme {
                RecommendationSettingsScreen(
                    state = state,
                    onAction = onAction,
                    onOpenPermissions = {},
                )
            }
        }
    }

    private companion object {
        const val REFRESH_TAG = "recommendation_refresh_action"
    }
}
