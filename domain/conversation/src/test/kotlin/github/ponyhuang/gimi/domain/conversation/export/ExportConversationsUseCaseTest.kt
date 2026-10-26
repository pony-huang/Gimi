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
    private val request = ConversationExportRequest(listOf("one"), "time", "one.jsonl", "application/x-ndjson", false)

    @Test
    fun singleExportAllowsOtherSessionsButAllExportIsBlocked() = runTest {
        gate.state.value = AgentRuntimeState.Busy(listOf(ActiveAgentTask(AgentTaskSource.CHAT, "other", AgentTaskPhase.GENERATING)))
        coEvery { repository.prepare("one") } returns request
        assertEquals(AgentMutationResult.Applied(request), exporter.prepare("one"))
        assertEquals(AgentMutationResult.BlockedByActiveAgent, exporter.prepare())
        coVerify(exactly = 0) { repository.prepare(null) }
    }

    @Test
    fun savingRechecksTasksStartedWhileChoosingDestination() = runTest {
        coEvery { repository.prepare("one") } returns request
        exporter.prepare("one")
        gate.state.value = AgentRuntimeState.Busy(listOf(ActiveAgentTask(AgentTaskSource.CHAT, "one", AgentTaskPhase.WAITING_FOR_CONFIRMATION)))
        assertEquals(AgentMutationResult.BlockedByActiveAgent, exporter.save(request, "content://export"))
        coVerify(exactly = 0) { repository.export(any(), any()) }
    }

    @Test
    fun allExportWithOnlyOneSessionStillChecksGlobalActivityWhenSaving() = runTest {
        gate.state.value = AgentRuntimeState.Busy(listOf(ActiveAgentTask(AgentTaskSource.CHAT, "other", AgentTaskPhase.EXECUTING_TOOL)))
        assertEquals(AgentMutationResult.BlockedByActiveAgent, exporter.save(request.copy(allSessions = true), "content://export"))
        coVerify(exactly = 0) { repository.export(any(), any()) }
    }
}
