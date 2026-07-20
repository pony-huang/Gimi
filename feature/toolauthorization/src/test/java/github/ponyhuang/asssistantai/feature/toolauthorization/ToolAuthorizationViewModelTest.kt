package github.ponyhuang.asssistantai.feature.toolauthorization

import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import app.cash.turbine.test
import github.ponyhuang.asssistantai.domain.conversation.runtime.ActiveAgentTask
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolAuthorizationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun activeAgentBlocksRepositoryMutationAndPublishesNotice() = runTest {
        val repository = repository()
        val gate = FakeGate(busy = true)
        val viewModel = ToolAuthorizationViewModel(
            repository,
            RunWhenAgentIdleUseCase(gate),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isMutationBlocked) state = awaitItem()
            viewModel.onAction(ToolAuthorizationAction.SetAllEnabled(false))
            do {
                state = awaitItem()
            } while (state.notice == null)

            verify(exactly = 0) { repository.setAllEnabled(any()) }
            assertTrue(state.isMutationBlocked)
            assertEquals("Agent 任务进行中，请先停止任务后再修改。", state.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun idleAgentAllowsRepositoryMutation() = runTest {
        val repository = repository()
        val viewModel = ToolAuthorizationViewModel(
            repository,
            RunWhenAgentIdleUseCase(FakeGate(busy = false)),
        )

        viewModel.onAction(ToolAuthorizationAction.SetEnabled("clock", false))
        advanceUntilIdle()

        verify { repository.setEnabled("clock", false) }
    }

    private fun repository(): ToolAuthorizationRepository = mockk(relaxed = true) {
        io.mockk.every { tools } returns MutableStateFlow(
            listOf(ToolDescriptor("clock", "clock", "Clock", true)),
        )
        io.mockk.every { revision } returns MutableStateFlow(0L)
    }
}

private class FakeGate(busy: Boolean) : AgentRuntimeGate {
    override val state = MutableStateFlow<AgentRuntimeState>(
        if (busy) {
            AgentRuntimeState.Busy(
                listOf(ActiveAgentTask(AgentTaskSource.CHAT, AgentTaskPhase.GENERATING)),
            )
        } else {
            AgentRuntimeState.Idle
        },
    )

    override suspend fun acquire(
        source: AgentTaskSource,
        phase: AgentTaskPhase,
    ): AgentRunLease = error("not used")

    override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
        if (state.value is AgentRuntimeState.Busy) {
            AgentMutationResult.BlockedByActiveAgent
        } else {
            AgentMutationResult.Applied(block())
        }
}
