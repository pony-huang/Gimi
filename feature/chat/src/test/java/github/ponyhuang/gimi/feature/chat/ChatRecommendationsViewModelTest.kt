package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRecommendationsViewModelTest {
    @Test
    fun exposesEnabledGlobalSnapshotItems() {
        val items = (1..6).map { index ->
            AgentRecommendation("id-$index", "task-$index", RecommendationCategory.GENERAL)
        }
        val repository = mockk<RecommendationRepository> {
            every { state } returns MutableStateFlow(
                RecommendationState(snapshot = RecommendationSnapshot(items, 1L)),
            )
        }

        val viewModel = ChatRecommendationsViewModel(repository)

        assertEquals(items, viewModel.recommendations.value)
    }
}
