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
import io.mockk.verifyOrder
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun failureRetriesWithFixedDelaysBeforeSucceeding() = runTest {
        val fixture = Fixture()
        var attempt = 0
        coEvery { fixture.generator.generate(any()) } answers {
            attempt++
            when (attempt) {
                1 -> throw IOException("boom-1")
                2 -> throw IOException("boom-2")
                else -> items()
            }
        }

        val outcome = fixture.refresher.refreshSafely()

        assertEquals(RecommendationRefreshOutcome.Success, outcome)
        verifyOrder {
            fixture.preferences.markRetrying(30L, "boom-1")
            fixture.preferences.markRetrying(60L, "boom-2")
        }
        verify { fixture.preferences.saveSnapshot(any()) }
        assertEquals(listOf(30_000L, 60_000L), fixture.delays)
    }

    @Test
    fun exhaustedRetriesMarksFailedWithLastError() = runTest {
        val fixture = Fixture()
        var attempt = 0
        coEvery { fixture.generator.generate(any()) } answers {
            attempt++
            throw IOException("boom-$attempt")
        }

        val outcome = fixture.refresher.refreshSafely()

        assertEquals(RecommendationRefreshOutcome.Failure, outcome)
        verifyOrder {
            fixture.preferences.markRetrying(30L, "boom-1")
            fixture.preferences.markRetrying(60L, "boom-2")
            fixture.preferences.markRetrying(120L, "boom-3")
            fixture.preferences.markRetrying(180L, "boom-4")
            fixture.preferences.markFailed("boom-5")
        }
        verify(exactly = 0) { fixture.preferences.saveSnapshot(any()) }
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 180_000L), fixture.delays)
    }

    @Test
    fun configurationErrorAlsoWalksRetryLadder() = runTest {
        val fixture = Fixture()
        var attempt = 0
        coEvery { fixture.generator.generate(any()) } answers {
            attempt++
            when (attempt) {
                1 -> throw IllegalStateException("No enabled model service with a configured model.")
                else -> items()
            }
        }

        val outcome = fixture.refresher.refreshSafely()

        assertEquals(RecommendationRefreshOutcome.Success, outcome)
        verifyOrder {
            fixture.preferences.markRetrying(30L, "No enabled model service with a configured model.")
        }
        assertEquals(listOf(30_000L), fixture.delays)
    }

    @Test
    fun cancellationPropagatesWithoutMarkingFailure() = runTest {
        val fixture = Fixture()
        coEvery { fixture.generator.generate(any()) } throws CancellationException("cancelled")

        try {
            fixture.refresher.refreshSafely()
            fail("Expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            // 预期向上传播，交由协程取消语义处理
        }

        verify(exactly = 0) { fixture.preferences.markRetrying(any(), any()) }
        verify(exactly = 0) { fixture.preferences.markFailed(any()) }
        assertTrue(fixture.delays.isEmpty())
    }

    @Test
    fun agentBusyReturnsRetryWithoutConsumingLadder() = runTest {
        val generator = mockk<RecommendationGenerator>()
        val capabilities = mockk<RecommendationCapabilitySource>()
        val context = mockk<RecommendationContextSource>()
        val preferences = mockk<RecommendationPreferences>(relaxed = true)
        val delays = mutableListOf<Long>()
        coEvery { capabilities.capabilities() } returns listOf(
            RecommendationCapability("clock", "local", "time"),
        )
        coEvery { context.currentContext() } returns RecommendationContext(mapOf("locale" to "zh-CN"))
        val refresher = RecommendationRefresher(
            generator,
            capabilities,
            context,
            preferences,
            RunWhenAgentIdleUseCase(BusyGate()),
            nowEpochMillis = { 123L },
            retryDelay = { delays += it },
        )

        val outcome = refresher.refreshSafely()

        assertEquals(RecommendationRefreshOutcome.Retry, outcome)
        assertTrue(delays.isEmpty())
        verify(exactly = 0) { preferences.markRetrying(any(), any()) }
        verify(exactly = 0) { preferences.markFailed(any()) }
    }

    private fun items() = (1..6).map { index ->
        AgentRecommendation("id-$index", "task-$index", RecommendationCategory.GENERAL)
    }

    /** 汇总成功路径用例共享的 mock 与记录型延迟注入。 */
    private inner class Fixture {
        val generator = mockk<RecommendationGenerator>()
        val capabilities = mockk<RecommendationCapabilitySource>()
        val context = mockk<RecommendationContextSource>()
        val preferences = mockk<RecommendationPreferences>(relaxed = true)
        val delays = mutableListOf<Long>()

        init {
            // 每次尝试都会先读取能力与上下文，统一打桩以隔离被测的重试行为
            coEvery { capabilities.capabilities() } returns listOf(
                RecommendationCapability("clock", "local", "time"),
            )
            coEvery { context.currentContext() } returns RecommendationContext(mapOf("locale" to "zh-CN"))
        }

        val refresher = RecommendationRefresher(
            generator,
            capabilities,
            context,
            preferences,
            RunWhenAgentIdleUseCase(IdleGate()),
            nowEpochMillis = { 123L },
            retryDelay = { delays += it },
        )
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

    private class BusyGate : AgentRuntimeGate {
        override val state: StateFlow<AgentRuntimeState> = MutableStateFlow(AgentRuntimeState.Idle)

        override suspend fun acquire(
            source: AgentTaskSource,
            sessionId: String?,
            phase: AgentTaskPhase,
        ): AgentRunLease = error("Not used")

        override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
            AgentMutationResult.BlockedByActiveAgent
    }
}
