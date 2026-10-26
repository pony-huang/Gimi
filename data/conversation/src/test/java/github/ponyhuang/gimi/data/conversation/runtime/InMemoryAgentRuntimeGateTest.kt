package github.ponyhuang.gimi.data.conversation.runtime

import android.content.Context
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryAgentRuntimeGateTest {
    private val context = mockk<Context>(relaxed = true)
    private fun createGate() = InMemoryAgentRuntimeGate(context)

    @Test
    fun sameSessionCannotBeAcquiredTwice() = runTest {
        val gate = createGate()
        val first = gate.acquire(AgentTaskSource.CHAT, sessionId = "session-a")

        try {
            gate.acquire(AgentTaskSource.SYSTEM_ASSISTANT, sessionId = "session-a")
            fail("Expected the second acquisition for the same session to be rejected")
        } catch (_: IllegalStateException) {
            // Expected: one conversation may only have one active writer.
        } finally {
            first.release()
        }
    }

    @Test
    fun differentSessionsCanStillRunConcurrently() = runTest {
        val gate = createGate()
        val first = gate.acquire(AgentTaskSource.CHAT, sessionId = "session-a")
        val second = gate.acquire(AgentTaskSource.SYSTEM_ASSISTANT, sessionId = "session-b")

        assertEquals(2, (gate.state.value as AgentRuntimeState.Busy).tasks.size)

        first.release()
        second.release()
    }

    @Test
    fun activeTasksBlockMutationsUntilEveryLeaseIsReleased() = runTest {
        val gate = createGate()
        val chat = gate.acquire(AgentTaskSource.CHAT, sessionId = "session-a")
        val assistant = gate.acquire(AgentTaskSource.SYSTEM_ASSISTANT)

        assertSame(
            AgentMutationResult.BlockedByActiveAgent,
            gate.runMutation { error("must not run") },
        )
        assertEquals(2, (gate.state.value as AgentRuntimeState.Busy).tasks.size)
        assertEquals(
            "session-a",
            (gate.state.value as AgentRuntimeState.Busy).tasks.first().sessionId,
        )

        chat.release()
        assertSame(
            AgentMutationResult.BlockedByActiveAgent,
            gate.runMutation { error("must not run") },
        )
        assistant.release()

        assertEquals(AgentRuntimeState.Idle, gate.state.value)
        assertEquals(7, (gate.runMutation { 7 } as AgentMutationResult.Applied).value)
    }

    @Test
    fun phaseUpdatesAndRepeatedReleaseAreSafe() = runTest {
        val gate = createGate()
        val lease = gate.acquire(AgentTaskSource.CHAT)

        lease.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
        assertEquals(
            AgentTaskPhase.WAITING_FOR_CONFIRMATION,
            (gate.state.value as AgentRuntimeState.Busy).tasks.single().phase,
        )

        lease.release()
        lease.release()
        lease.updatePhase(AgentTaskPhase.EXECUTING_TOOL)
        assertEquals(AgentRuntimeState.Idle, gate.state.value)
    }

    @Test
    fun taskAcquisitionWaitsForAnAtomicMutation() = runTest {
        val gate = createGate()
        val mutationStarted = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        val mutation = async {
            gate.runMutation {
                mutationStarted.complete(Unit)
                finishMutation.await()
            }
        }
        mutationStarted.await()

        val acquire = async { gate.acquire(AgentTaskSource.CHAT) }
        assertEquals(AgentRuntimeState.Idle, gate.state.value)
        finishMutation.complete(Unit)
        mutation.await()

        val lease = acquire.await()
        assertEquals(1, (gate.state.value as AgentRuntimeState.Busy).tasks.size)
        lease.release()
    }

    @Test
    fun snapshotBlocksItsActiveSessionButAllowsOtherIdleSessions() = runTest {
        val gate = createGate()
        val lease = gate.acquire(AgentTaskSource.CHAT, "active", AgentTaskPhase.WAITING_FOR_INPUT)
        assertSame(AgentMutationResult.BlockedByActiveAgent, gate.readSessionSnapshot("active") { error("must not read") })
        assertSame(AgentMutationResult.BlockedByActiveAgent, gate.readSessionSnapshot(null) { error("must not read") })
        assertEquals(AgentMutationResult.Applied(42), gate.readSessionSnapshot("idle") { 42 })
        lease.release()
    }

    @Test
    fun newTasksWaitForSnapshotAndCancellationReleasesItsLock() = runTest {
        val gate = createGate()
        val started = CompletableDeferred<Unit>()
        val snapshot = async {
            gate.readSessionSnapshot("session") {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        started.await()
        val task = async { gate.acquire(AgentTaskSource.CHAT, "session") }
        runCurrent()
        assertEquals(false, task.isCompleted)
        snapshot.cancel()
        snapshot.join()
        task.await().release()
        assertEquals(AgentMutationResult.Applied(Unit), gate.readSessionSnapshot("session") {})
    }

    @Test
    fun failedSnapshotReleasesItsLock() = runTest {
        val gate = createGate()
        try {
            gate.readSessionSnapshot("session") { throw java.io.IOException("read failed") }
            fail("Expected read failure")
        } catch (_: java.io.IOException) {
            gate.acquire(AgentTaskSource.CHAT, "session").release()
        }
    }
}
