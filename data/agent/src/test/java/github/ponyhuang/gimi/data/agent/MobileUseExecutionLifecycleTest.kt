package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.events.Event
import com.google.adk.kt.runners.InMemoryRunner
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileUseExecutionLifecycleTest {
    private val repository = mockk<MobileUseRepository>(relaxed = true)

    private fun execution(events: Flow<Event>): AgentChatRunner.Execution {
        val runner = mockk<InMemoryRunner>()
        coEvery { runner.runAsync(any(), any(), any(), any(), any(), any()) } returns events
        val metadata = ToolRunMetadata.of(
            ModelRuntimeMetadata("service", ApiProtocol.Standard, "vision", "https://example.com", true),
            null, true, mobileUseOwner = "turn",
        )
        return AgentChatRunner.Execution("user", "chat", runner, metadata, repository)
    }

    @Test
    fun completionAndResumeReleaseExecutionWithoutClosingDisplay() = runTest {
        val execution = execution(emptyFlow())
        execution.send("hello").collect()
        execution.respondToInputRequest("input", "get_user_choice", mapOf("value" to "yes")).collect()
        coVerify(exactly = 2) { repository.registerExecution("turn", "chat") }
        coVerify(exactly = 2) { repository.finishExecution("turn") }
        coVerify(exactly = 0) { repository.closeSession(any()) }
    }

    @Test
    fun failureReleasesExecutionAndPreservesOriginalFailure() = runTest {
        val execution = execution(flow { throw IllegalStateException("run failed") })
        val failure = runCatching { execution.send("hello").collect() }.exceptionOrNull()
        assertEquals("run failed", failure?.message)
        coVerify { repository.registerExecution("turn", "chat") }
        coVerify { repository.finishExecution("turn") }
        coVerify(exactly = 0) { repository.closeSession(any()) }
    }

    @Test
    fun cancellationStillReleasesExecution() = runTest {
        val started = CompletableDeferred<Unit>()
        val execution = execution(flow { started.complete(Unit); awaitCancellation() })
        val job = launch { execution.send("hello").collect() }
        started.await()
        job.cancelAndJoin()
        coVerify { repository.finishExecution("turn") }
        coVerify(exactly = 0) { repository.closeSession(any()) }
    }
}
