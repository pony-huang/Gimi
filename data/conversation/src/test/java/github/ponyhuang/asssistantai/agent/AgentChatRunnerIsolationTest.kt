package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentChatRunnerIsolationTest {
    @Test
    fun sessionsOwnIndependentRunnersAndConfirmationReusesItsSessionRunner() = runTest {
        val createdSelections = mutableListOf<ModelSelection?>()
        val runner = AgentChatRunner(
            factory = { selection ->
                createdSelections += selection
                mockk<BaseAgent>(relaxed = true)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
        )
        val selection = ModelSelection("service", "group", "model")

        runner.send("user", "session-a", selection, "a")
        runner.send("user", "session-a", selection, "a2")
        runner.send("user", "session-b", selection, "b")

        assertEquals(listOf(selection, selection), createdSelections)

        runner.respondToToolConfirmation("user", "session-a", "confirmation", true)
        assertEquals(2, createdSelections.size)

        runner.releaseSession("session-a")
        runner.send("user", "session-a", selection, "a3")
        assertEquals(3, createdSelections.size)
    }

    @Test
    fun modelOrConfigurationRevisionChangeRebuildsOnlyTheAddressedSession() = runTest {
        var revision = 0
        var creations = 0
        val runner = AgentChatRunner(
            factory = {
                creations += 1
                mockk<BaseAgent>(relaxed = true)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            configurationRevision = { revision },
        )
        val first = ModelSelection("service", "group", "first")
        val second = ModelSelection("service", "group", "second")

        runner.send("user", "session-a", first, "a")
        runner.send("user", "session-b", first, "b")
        runner.send("user", "session-a", second, "a2")
        assertEquals(3, creations)

        revision += 1
        runner.send("user", "session-b", first, "b2")
        assertEquals(4, creations)
    }
}
