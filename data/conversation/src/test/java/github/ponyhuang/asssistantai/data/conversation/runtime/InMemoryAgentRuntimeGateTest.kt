package github.ponyhuang.asssistantai.data.conversation.runtime

import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InMemoryAgentRuntimeGateTest {
    @Test
    fun activeTasksBlockMutationsUntilEveryLeaseIsReleased() = runTest {
        val gate = InMemoryAgentRuntimeGate()
        val chat = gate.acquire(AgentTaskSource.CHAT, sessionId = "session-a")
        val voice = gate.acquire(AgentTaskSource.BLUETOOTH_VOICE)

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
        voice.release()

        assertEquals(AgentRuntimeState.Idle, gate.state.value)
        assertEquals(7, (gate.runMutation { 7 } as AgentMutationResult.Applied).value)
    }

    @Test
    fun phaseUpdatesAndRepeatedReleaseAreSafe() = runTest {
        val gate = InMemoryAgentRuntimeGate()
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
        val gate = InMemoryAgentRuntimeGate()
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
}
