package github.ponyhuang.gimi.data.agent.plugins

import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemoryPersistencePluginTest {

    private fun event(id: String, role: String, text: String, partial: Boolean = false) = Event(
        id = id,
        author = "test",
        content = Content(role = role, parts = listOf(Part(text = text))),
        partial = partial,
    )

    @Test
    fun afterAgentWritesOnlyNewNonPartialEventsPerTurn() = runTest {
        val session = Session(
            key = SessionKey("Gimi", "user-default", "session-1"),
            events = mutableListOf(
                event("e1", Role.USER, "Hello"),
                event("e2", Role.MODEL, "Hi", partial = true),
                event("e3", Role.MODEL, "Hi there"),
            ),
        )
        val context = mockk<CallbackContext>(relaxed = true)
        every { context.session } returns session

        val plugin = MemoryPersistencePlugin()
        plugin.afterAgent(context)
        // 第二轮只有新增事件才写入，且 partial 事件不参与。
        session.events.add(event("e4", Role.USER, "Second question"))
        plugin.afterAgent(context)

        coVerify(exactly = 1) { context.addEventsToMemory(listOf(session.events[0], session.events[2])) }
        coVerify(exactly = 1) { context.addEventsToMemory(listOf(session.events[3])) }
    }

    @Test
    fun afterAgentSkipsWriteWhenNothingNew() = runTest {
        val session = Session(
            key = SessionKey("Gimi", "user-default", "session-1"),
            events = mutableListOf(event("e1", Role.USER, "Hello")),
        )
        val context = mockk<CallbackContext>(relaxed = true)
        every { context.session } returns session

        val plugin = MemoryPersistencePlugin()
        plugin.afterAgent(context)
        plugin.afterAgent(context)

        coVerify(exactly = 1) { context.addEventsToMemory(any()) }
    }
}
