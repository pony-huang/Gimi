package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AgentChatRunnerIsolationTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun sessionsOwnIndependentRunnersAndConfirmationReusesItsSessionRunner() = runTest {
        val createdSelections = mutableListOf<ModelSelection?>()
        val runner = AgentChatRunner(
            factory = { selection ->
                createdSelections += selection
                mockk<LlmAgent>(relaxed = true)
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
                mockk<LlmAgent>(relaxed = true)
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

    @Test
    fun runnerCacheEvictsLeastRecentlyUsedSession() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = {
                creations += 1
                mockk<LlmAgent>(relaxed = true)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
        )

        repeat(AgentChatRunner.MAX_CACHED_RUNNERS + 1) { index ->
            runner.send("user", "session-$index", text = "message")
        }
        runner.send("user", "session-0", text = "again")

        assertEquals(AgentChatRunner.MAX_CACHED_RUNNERS + 2, creations)
    }

    @Test
    fun conversationToolConfigurationChangeRebuildsOnlyThatSession() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = { _, _, _ ->
                creations += 1
                mockk<LlmAgent>(relaxed = true)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
        )
        val selection = ModelSelection("service", "group", "model")
        val clockOnly = ConversationToolConfiguration(enabledLocalToolIds = setOf("clock"))
        val clockAndLocation = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "location"),
        )

        runner.send("user", "session-a", selection, "a", toolConfiguration = clockOnly)
        runner.send("user", "session-b", selection, "b", toolConfiguration = clockOnly)
        runner.send("user", "session-a", selection, "a2", toolConfiguration = clockAndLocation)

        assertEquals(3, creations)
    }
}
