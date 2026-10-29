package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 远程运行边界：SDK 失败事件、部分输出和取消必须分别处理。 */
class ResilientRemoteAgentTest {
    @Test
    fun failedSdkEventBecomesLocalResultAfterPartialOutput() = runBlocking {
        val remote = ResilientRemoteAgent(object : BaseAgent("remote") {
            override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
                emit(Event(author = name, partial = true, content = Content(parts = listOf(Part(text = "partial")))))
                emit(Event(author = name, errorMessage = "sensitive upstream diagnostic"))
            }
        })

        val events = remote.runAsync(context(remote)).toList()

        assertEquals("partial", events.first().content?.parts?.single()?.text)
        assertFalse(events.last().isFinalResponse)
        assertTrue(events.none { it.errorCode != null || it.errorMessage != null })
        assertEquals(events[1].functionCalls().single().id, events.last().functionResponses().single().id)
        assertEquals("unknown", events.last().functionResponses().single().response["execution_status"])
        assertFalse(events.toString().contains("sensitive upstream diagnostic"))
    }

    @Test
    fun userCancellationDoesNotBecomeRemoteFailure() = runBlocking {
        val remote = ResilientRemoteAgent(object : BaseAgent("remote") {
            override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
                throw CancellationException("user stopped")
            }
        })
        val events = mutableListOf<Event>()
        try {
            remote.runAsync(context(remote)).collect { events += it }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(events.isEmpty())
        }
    }

    private fun context(agent: BaseAgent) = InvocationContext(
        agent = agent,
        session = Session(SessionKey("test", "user", "session")),
    )
}
