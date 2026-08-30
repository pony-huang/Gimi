package github.ponyhuang.gimi.domain.toolauthorization.usecase

import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetToolAuthorizationUseCaseTest {

    @Test
    fun idleGateAppliesCustomizationMutation() = runTest {
        val repository = repository()
        val useCase = useCase(repository, busy = false)

        val result = useCase.setCustomizationEnabled(true)

        assertEquals(ToolAuthorizationMutationResult.Applied, result)
        verify { repository.setCustomizationEnabled(true) }
    }

    @Test
    fun busyGateRejectsCustomizationMutationWithoutWritingRepository() = runTest {
        val repository = repository()
        val useCase = useCase(repository, busy = true)

        val result = useCase.setCustomizationEnabled(true)

        assertEquals(ToolAuthorizationMutationResult.BlockedByActiveAgent, result)
        verify(exactly = 0) { repository.setCustomizationEnabled(any()) }
    }

    @Test
    fun idleGateAppliesToolToggle() = runTest {
        val repository = repository()
        val useCase = useCase(repository, busy = false)

        val result = useCase.setToolEnabled("clock", false)

        assertEquals(ToolAuthorizationMutationResult.Applied, result)
        verify { repository.setEnabled("clock", false) }
    }

    @Test
    fun busyGateRejectsToolToggleWithoutWritingRepository() = runTest {
        val repository = repository()
        val useCase = useCase(repository, busy = true)

        val result = useCase.setToolEnabled("clock", false)

        assertEquals(ToolAuthorizationMutationResult.BlockedByActiveAgent, result)
        verify(exactly = 0) { repository.setEnabled(any(), any()) }
    }

    @Test
    fun agentRuntimeStateReflectsGateState() = runTest {
        val useCase = useCase(repository(), busy = true)

        assertTrue(useCase.agentRuntimeState.value.isBusy)
    }

    private fun useCase(
        repository: ToolAuthorizationRepository,
        busy: Boolean,
    ) = SetToolAuthorizationUseCase(
        repository,
        RunWhenAgentIdleUseCase(
            if (busy) FakeAgentRuntimeGate.busy() else FakeAgentRuntimeGate(),
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
