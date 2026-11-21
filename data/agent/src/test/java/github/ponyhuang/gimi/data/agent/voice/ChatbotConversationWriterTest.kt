package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.chatbot.ChatbotConversationResolver
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Chatbot 持久化保护历史恢复、工具隔离、写入互斥、失败重试边界与分区写入。 */
class ChatbotConversationWriterTest {
    private val sessions = InMemorySessionService()
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val resolver = mockk<ChatbotConversationResolver>()
    private val gate = FakeAgentRuntimeGate()
    private val key = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.CHATBOT_USER_ID, "conversation")

    private fun store(service: SessionService = sessions) = ChatbotConversationWriter(service, conversations, resolver, gate)

    @Test
    fun persistedMessagesSurviveReopeningAndToolCallsAreExcluded() = runTest {
        val session = sessions.createSession(key)
        sessions.appendEvent(session, Event(author = "assistant", content = Content(
            role = Role.MODEL,
            parts = listOf(Part(text = "历史回复"), Part(functionCall = FunctionCall(name = "tool", id = "call"))),
        )))
        val call = store().bind("conversation")
        assertEquals(listOf(Part(text = "历史回复")), call.acquire().single().parts)
        call.enqueue(Content.fromText(Role.USER, "问题"))
        call.enqueue(Content.fromText(Role.MODEL, "已听回复"))
        call.flush()
        call.flush()
        call.release()
        val reopened = store().bind("conversation")
        assertEquals(listOf("历史回复", "问题", "已听回复"), reopened.acquire().map { it.parts.single().text })
        reopened.release()
        assertEquals(3, sessions.getSession(key)!!.events.size)
    }

    @Test
    fun resolveMemoizesTheCurrentConversationForWriteOnlyCallers() = runTest {
        coEvery { resolver.resolveCurrentOrCreate() } returns "conversation"
        sessions.createSession(key)
        val call = store().bind(null)

        assertEquals("conversation", call.resolve())
        assertEquals("conversation", call.resolve())
        coVerify(exactly = 1) { resolver.resolveCurrentOrCreate() }

        call.enqueue(Content.fromText(Role.USER, "问题"))
        call.flush()
        assertEquals(1, sessions.getSession(key)!!.events.size)
    }

    @Test
    fun retryAfterCommittedWriteDoesNotDuplicateEvent() = runTest {
        sessions.createSession(key)
        val service = mockk<SessionService>()
        coEvery { service.getSession(key) } coAnswers { sessions.getSession(key) }
        coEvery { service.flush(any()) } returns Unit
        coEvery { service.appendEvent(any(), any()) } coAnswers {
            sessions.appendEvent(firstArg(), secondArg())
            throw IllegalStateException("Write acknowledgement lost")
        }
        val call = store(service).bind("conversation")
        call.acquire()
        call.enqueue(Content.fromText(Role.USER, "问题"))
        try {
            call.flush()
            fail("Expected persistence failure")
        } catch (failure: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.Persistence, failure.failure)
        }
        call.flush()
        assertEquals(1, sessions.getSession(key)!!.events.size)
        call.release()
    }

    @Test
    fun missingConversationReleasesLeaseAndBusyConversationIsRejected() = runTest {
        try {
            store().bind("conversation").acquire()
            fail("Expected missing conversation")
        } catch (failure: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.Persistence, failure.failure)
        }
        assertEquals(1, gate.releaseCount)
        sessions.createSession(key)
        val call = store().bind("conversation")
        call.acquire()
        gate.acquireException = AgentSessionBusyException("conversation")
        try {
            store().bind("conversation").acquire()
            fail("Expected busy conversation")
        } catch (failure: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.ConversationBusy, failure.failure)
        }
        gate.acquireException = null
        call.release()
        store().bind("conversation").also { it.acquire(); it.release() }
    }

    @Test
    fun cancellationPropagatesAndReleasesLease() = runTest {
        val service = mockk<SessionService>()
        coEvery { service.getSession(key) } throws CancellationException("Cancelled")
        try {
            store(service).bind("conversation").acquire()
            fail("Expected cancellation")
        } catch (cancelled: CancellationException) {
            assertTrue(cancelled.message!!.contains("Cancelled"))
            assertEquals(1, gate.releaseCount)
        }
        sessions.createSession(key)
        store().bind("conversation").also { it.acquire(); it.release() }
    }

    @Test
    fun unboundCallsResolveTheChatbotPartitionCurrentConversation() = runTest {
        coEvery { resolver.resolveCurrentOrCreate() } returns "conversation"
        sessions.createSession(key)
        val call = store().bind(null)
        assertEquals(emptyList<Content>(), call.acquire())
        call.enqueue(Content.fromText(Role.USER, "问题"))
        call.flush()
        call.release()
        // 未显式绑定会话时经 Chatbot resolver 解析当前会话，事件写进 chatbot 分区。
        assertEquals(1, sessions.getSession(key)!!.events.size)
    }
}
