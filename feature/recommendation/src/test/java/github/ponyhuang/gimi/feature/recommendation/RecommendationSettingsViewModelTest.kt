package github.ponyhuang.gimi.feature.recommendation

import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationSettingsViewModelTest {
    @Test
    fun actionsUpdateGlobalRepository() {
        val repository = mockk<RecommendationRepository>(relaxed = true) {
            every { state } returns MutableStateFlow(RecommendationState())
        }
        val viewModel = RecommendationSettingsViewModel(repository)

        viewModel.onAction(RecommendationSettingsAction.SetEnabled(false))
        viewModel.onAction(RecommendationSettingsAction.SetIntervalHours(6))
        viewModel.onAction(RecommendationSettingsAction.RefreshNow)

        verify { repository.setEnabled(false) }
        verify { repository.setIntervalHours(6) }
        verify { repository.requestRefresh() }
        assertEquals(RecommendationSettings.SUPPORTED_INTERVAL_HOURS, viewModel.uiState.value.intervals)
    }
}
