package github.ponyhuang.gimi.feature.toolauthorization

import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.every
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
    fun activeAgentBlocksCustomizationToggleAndEmitsBusyEffect() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository, busy = true)

        viewModel.effects.test {
            viewModel.onAction(ToolAuthorizationAction.SetCustomizationEnabled(true))

            assertEquals(
                ToolAuthorizationEffect.ShowMessage(ToolAuthorizationMessage.AgentBusy),
                awaitItem(),
            )
            verify(exactly = 0) { repository.setCustomizationEnabled(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeAgentMarksMutationBlockedInUiState() = runTest {
        val viewModel = viewModel(repository(), busy = true)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isMutationBlocked) state = awaitItem()

            assertTrue(state.isMutationBlocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun idleAgentAllowsCustomizationToggle() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository, busy = false)

        viewModel.onAction(ToolAuthorizationAction.SetCustomizationEnabled(true))
        advanceUntilIdle()

        verify { repository.setCustomizationEnabled(true) }
    }

    private fun viewModel(
        repository: ToolAuthorizationRepository,
        busy: Boolean,
    ) = ToolAuthorizationViewModel(
        repository,
        SetToolAuthorizationUseCase(
            repository,
            RunWhenAgentIdleUseCase(
                if (busy) FakeAgentRuntimeGate.busy() else FakeAgentRuntimeGate(),
            ),
        ),
    )

    private fun repository(): ToolAuthorizationRepository = mockk(relaxed = true) {
        every { tools } returns MutableStateFlow(
            listOf(ToolDescriptor("clock", "clock", "Clock", true)),
        )
        every { revision } returns MutableStateFlow(0L)
        every { isCustomizationEnabled } returns MutableStateFlow(false)
    }
}
