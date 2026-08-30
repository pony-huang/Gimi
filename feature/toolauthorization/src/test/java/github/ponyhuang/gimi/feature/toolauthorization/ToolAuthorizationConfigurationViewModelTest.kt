package github.ponyhuang.gimi.feature.toolauthorization

import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.toolauthorization.usecase.SetToolAuthorizationUseCase
import github.ponyhuang.gimi.domain.toolauthorization.usecase.ToolAuthorizationMutationResult
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolAuthorizationConfigurationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun searchAndFilterCombineInVisibleTools() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository, busy = false)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(ToolAuthorizationConfigurationAction.Search("location"))
            viewModel.onAction(ToolAuthorizationConfigurationAction.SetFilter(ToolAuthorizationFilter.DISABLED))
            var state = awaitItem()
            while (state.visibleTools.size != 1) {
                state = awaitItem()
            }

            assertEquals(listOf("get_location"), state.visibleTools.map { it.id })
        }
    }

    @Test
    fun activeAgentBlocksToolToggleAndEmitsBusyEffect() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository, busy = true)

        viewModel.effects.test {
            viewModel.onAction(ToolAuthorizationConfigurationAction.SetEnabled("clock", false))

            assertEquals(
                ToolAuthorizationEffect.ShowMessage(ToolAuthorizationMessage.AgentBusy),
                awaitItem(),
            )
            verify(exactly = 0) { repository.setEnabled(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun idleAgentAllowsToolToggle() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository, busy = false)

        viewModel.onAction(ToolAuthorizationConfigurationAction.SetEnabled("clock", false))
        advanceUntilIdle()

        verify { repository.setEnabled("clock", false) }
    }

    private fun viewModel(
        repository: ToolAuthorizationRepository,
        busy: Boolean,
    ) = ToolAuthorizationConfigurationViewModel(
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
            listOf(
                ToolDescriptor("clock", "clock", "Clock", true),
                ToolDescriptor("get_location", "get_location", "Location", false),
            ),
        )
        every { revision } returns MutableStateFlow(0L)
        every { isCustomizationEnabled } returns MutableStateFlow(true)
    }
}
