package github.ponyhuang.gimi.domain.conversation.export

import github.ponyhuang.gimi.domain.conversation.runtime.ActiveAgentTask
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportConversationsUseCaseTest {
    private val repository = mockk<ConversationExportRepository>(relaxed = true)
    private val gate = FakeAgentRuntimeGate()
    private val exporter = ExportConversationsUseCase(repository, gate)
    private val target = ConversationExportSession("user-default", "one")
    private val request = ConversationExportRequest("user-default", "one", "time", "one.jsonl", "application/x-ndjson")

    @Test
    fun exportAllowsOtherSessionsButTargetSessionIsBlocked() = runTest {
        gate.state.value = AgentRuntimeState.Busy(
            listOf(ActiveAgentTask(AgentTaskSource.CHAT, "other", AgentTaskPhase.GENERATING)),
        )
        coEvery { repository.prepare(target) } returns request
        assertEquals(AgentMutationResult.Applied(request), exporter.prepare(target))
        gate.state.value = AgentRuntimeState.Busy(
            listOf(ActiveAgentTask(AgentTaskSource.CHATBOT, "one", AgentTaskPhase.GENERATING)),
        )
        assertEquals(AgentMutationResult.BlockedByActiveAgent, exporter.prepare(target))
        coVerify(exactly = 1) { repository.prepare(target) }
    }

    @Test
    fun savingRechecksTasksStartedWhileChoosingDestination() = runTest {
        coEvery { repository.prepare(target) } returns request
        exporter.prepare(target)
        gate.state.value = AgentRuntimeState.Busy(
            listOf(ActiveAgentTask(AgentTaskSource.CHAT, "one", AgentTaskPhase.WAITING_FOR_CONFIRMATION)),
        )
        assertEquals(AgentMutationResult.BlockedByActiveAgent, exporter.save(request, "content://export"))
        coVerify(exactly = 0) { repository.export(any(), any()) }
    }

    @Test
    fun savingPassesThroughWhenTargetStaysIdle() = runTest {
        coEvery { repository.prepare(target) } returns request
        exporter.prepare(target)
        gate.state.value = AgentRuntimeState.Busy(
            listOf(ActiveAgentTask(AgentTaskSource.CHAT, "other", AgentTaskPhase.EXECUTING_TOOL)),
        )
        assertEquals(AgentMutationResult.Applied(Unit), exporter.save(request, "content://export"))
        coVerify(exactly = 1) { repository.export(request, "content://export") }
    }
}
