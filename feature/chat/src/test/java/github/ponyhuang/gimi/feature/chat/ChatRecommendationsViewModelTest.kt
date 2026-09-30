package github.ponyhuang.gimi.feature.chat

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatRecommendationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposesSnapshotUpdatesAndClearsItemsWhileDisabled() = runTest {
        val items = (1..6).map { index ->
            AgentRecommendation("id-$index", "task-$index", RecommendationCategory.GENERAL)
        }
        val state = MutableStateFlow(RecommendationState(snapshot = RecommendationSnapshot(items, 1L)))
        val repository = mockk<RecommendationRepository> { every { this@mockk.state } returns state }

        val viewModel = ChatRecommendationsViewModel(repository)

        assertEquals(items, viewModel.recommendations.value)
        viewModel.recommendations.test {
            assertEquals(items, awaitItem())
            val updatedItems = items.map { it.copy(prompt = "updated-${it.id}") }
            state.value = state.value.copy(snapshot = RecommendationSnapshot(updatedItems, 2L))
            assertEquals(updatedItems, awaitItem())
            state.value = state.value.copy(settings = state.value.settings.copy(enabled = false))
            assertEquals(emptyList<AgentRecommendation>(), awaitItem())
            state.value = state.value.copy(settings = state.value.settings.copy(enabled = true))
            assertEquals(updatedItems, awaitItem())
        }
    }
}
