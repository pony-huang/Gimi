package github.ponyhuang.gimi.data.recommendation

import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCapability
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationCapabilitySource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationContextSource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationGenerator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationRefresherTest {
    @Test
    fun successfulGenerationCommitsTimestampedSnapshot() = runTest {
        val generator = mockk<RecommendationGenerator>()
        val capabilities = mockk<RecommendationCapabilitySource>()
        val context = mockk<RecommendationContextSource>()
        val preferences = mockk<RecommendationPreferences>(relaxed = true)
        coEvery { capabilities.capabilities() } returns listOf(
            RecommendationCapability("clock", "local", "time"),
        )
        coEvery { context.currentContext() } returns RecommendationContext(mapOf("locale" to "zh-CN"))
        coEvery { generator.generate(any()) } returns items()
        val refresher = RecommendationRefresher(
            generator,
            capabilities,
            context,
            preferences,
            RunWhenAgentIdleUseCase(IdleGate()),
            nowEpochMillis = { 123L },
        )

        val outcome = refresher.refresh()

        assertEquals(RecommendationRefreshOutcome.Success, outcome)
        verify { preferences.saveSnapshot(match { it.generatedAtEpochMillis == 123L }) }
    }

    private fun items() = (1..6).map { index ->
        AgentRecommendation("id-$index", "task-$index", RecommendationCategory.GENERAL)
    }

    private class IdleGate : AgentRuntimeGate {
        override val state: StateFlow<AgentRuntimeState> = MutableStateFlow(AgentRuntimeState.Idle)

        override suspend fun acquire(
            source: AgentTaskSource,
            sessionId: String?,
            phase: AgentTaskPhase,
        ): AgentRunLease = error("Not used")

        override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
            AgentMutationResult.Applied(block())
    }
}
