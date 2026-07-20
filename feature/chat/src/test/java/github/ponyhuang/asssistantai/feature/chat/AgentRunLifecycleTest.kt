package github.ponyhuang.asssistantai.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunLifecycleTest {

    @Test
    fun partialEventKeepsAgentRunning() {
        val result = AgentRunStatus(isRunning = true, turnComplete = false)
            .afterEvent(partial = true, turnComplete = false)

        assertTrue(result.isRunning)
        assertFalse(result.turnComplete)
    }

    @Test
    fun intermediateNonPartialEventKeepsAgentRunning() {
        val result = AgentRunStatus(isRunning = true, turnComplete = false)
            .afterEvent(partial = false, turnComplete = false)

        assertTrue(result.isRunning)
        assertFalse(result.turnComplete)
    }

    @Test
    fun turnCompleteEventEndsAgentRun() {
        val result = AgentRunStatus(isRunning = true, turnComplete = false)
            .afterEvent(partial = false, turnComplete = true)

        assertFalse(result.isRunning)
        assertTrue(result.turnComplete)
    }

    @Test
    fun staleTokenCannotOwnNewRunCompletion() {
        val ownership = AgentRunOwnership()
        val oldRun = ownership.claim()
        val newRun = ownership.claim()

        assertFalse(ownership.isOwnedBy(oldRun))
        assertTrue(ownership.isOwnedBy(newRun))
    }

    @Test
    fun invalidatingRunRevokesItsOwnership() {
        val ownership = AgentRunOwnership()
        val cancelledRun = ownership.claim()

        ownership.invalidate()

        assertFalse(ownership.isOwnedBy(cancelledRun))
    }
}
