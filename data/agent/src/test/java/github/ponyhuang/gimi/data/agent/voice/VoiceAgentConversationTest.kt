package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatExecution
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.model.GetUserChoiceToolName
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 聊天模式语音的完整 Agent 执行层：正文增量、完全批准、挂起输入与失败映射。 */
class VoiceAgentConversationTest {
    private val runner = mockk<AgentChatRunner>()
    private val sessions = mockk<SessionService>()
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val gate = FakeAgentRuntimeGate()
    private val selection = ModelSelection("service", "group", "model")
    private val conversation = VoiceAgentConversation(runner, sessions, conversations, gate).bind("conv", selection)

    private fun userEcho(text: String) = Event(author = "user", content = Content.fromText(Role.USER, text))

    private fun modelText(text: String, partial: Boolean = false) = Event(
        author = AgentSessionIdentity.ASSISTANT_NAME,
        content = Content.fromText(Role.MODEL, text),
        partial = partial,
    )

    private fun confirmationCall(id: String) = Event(
        author = AgentSessionIdentity.ASSISTANT_NAME,
        content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME, id = id,
        )))),
    )

    private fun inputRequestCall(id: String, message: String) = Event(
        author = AgentSessionIdentity.ASSISTANT_NAME,
        content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
            name = GetUserChoiceToolName, id = id, args = mapOf("message" to message, "options" to listOf("A", "B")),
        )))),
    )

    private fun toolResponse(id: String = "1") = Event(
        author = AgentSessionIdentity.ASSISTANT_NAME,
        content = Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(
            name = "tool", id = id, response = emptyMap(),
        )))),
    )

    private fun stubExecution(
        send: Flow<Event> = emptyFlow(),
        resume: Flow<Event> = emptyFlow(),
        inputResume: Flow<Event> = emptyFlow(),
    ): AgentChatExecution {
        val execution = mockk<AgentChatExecution>()
        coEvery { execution.send(any(), any(), any()) } returns send
        coEvery { execution.respondToToolConfirmation(any(), any()) } returns resume
        coEvery { execution.respondToInputRequest(any(), any(), any()) } returns inputResume
        coEvery { runner.createExecution(any(), any(), any(), any(), any(), any(), any()) } returns execution
        coEvery { conversations.conversationToolConfiguration("conv") } returns null
        return execution
    }

    private suspend fun acquiredCall() {
        val key = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, "conv")
        coEvery { sessions.getSession(key) } returns mockk()
        conversation.acquire()
    }

    @Test
    fun deltasStreamOnceAndConsolidatedEventsDoNotRepeatThem() = runTest {
        stubExecution(send = flowOf(
            userEcho("问题"),
            modelText("先说半", partial = true),
            modelText("先说半句。", partial = true),
            modelText("先说半句。", partial = false),
        ))
        acquiredCall()
        val deltas = mutableListOf<String>()
        conversation.runTurn("问题") { deltas += it }
        assertEquals(listOf("先说半", "先说半句。"), deltas)
    }

    @Test
    fun newModelRoundAfterToolResponseStartsWithoutCarryingThePreviousPartialFlag() = runTest {
        stubExecution(send = flowOf(
            userEcho("问题"),
            modelText("第一轮。", partial = true),
            toolResponse(),
            // 工具轮之后的模型只用一个非流式事件给出回答；上一轮的 partial 状态不能把它吞掉。
            modelText("第二轮。", partial = false),
        ))
        acquiredCall()
        val deltas = mutableListOf<String>()
        conversation.runTurn("问题") { deltas += it }
        assertEquals(listOf("第一轮。", "第二轮。"), deltas)
    }

    @Test
    fun confirmationRequestsAreFullyApprovedAndTheResumedStreamKeepsEmitting() = runTest {
        val execution = stubExecution(
            send = flowOf(userEcho("问题"), modelText("我来查。", partial = true), confirmationCall("c1")),
            resume = flowOf(modelText("查到了。", partial = true)),
        )
        acquiredCall()
        val deltas = mutableListOf<String>()
        conversation.runTurn("问题") { deltas += it }
        assertEquals(listOf("我来查。", "查到了。"), deltas)
        coVerify(exactly = 1) { execution.respondToToolConfirmation("c1", true) }
    }

    @Test
    fun userInputRequestPausesTheTurnAndNextUtteranceResumesWithTheChoicePayload() = runTest {
        val execution = stubExecution(
            send = flowOf(userEcho("问题"), inputRequestCall("q1", "选哪个？")),
            inputResume = flowOf(modelText("已选择。")),
        )
        acquiredCall()
        val paused = mutableListOf<String>()
        conversation.runTurn("问题") { paused += it }
        assertTrue(paused.isEmpty())
        val resumed = mutableListOf<String>()
        conversation.runTurn("A") { resumed += it }
        assertEquals(listOf("已选择。"), resumed)
        coVerify(exactly = 1) {
            execution.respondToInputRequest("q1", GetUserChoiceToolName, mapOf("choice" to "A"))
        }
    }

    @Test
    fun executionFailureIsMappedToTheGenerationFailure() = runTest {
        stubExecution(send = flow { throw IllegalStateException("boom") })
        acquiredCall()
        try {
            conversation.runTurn("问题") { }
            fail("Expected generation failure")
        } catch (error: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.Generation, error.failure)
        }
    }

    @Test
    fun acquireTakesTheVoiceLeaseOnceAndBusySessionIsTranslated() = runTest {
        acquiredCall()
        conversation.acquire()
        val acquisition = gate.acquisitions.single()
        assertEquals(AgentTaskSource.REALTIME_VOICE, acquisition.source)
        assertEquals("conv", acquisition.sessionId)
        assertEquals(AgentTaskPhase.WAITING_FOR_INPUT, acquisition.phase)

        val busy = FakeAgentRuntimeGate()
        busy.acquireException = AgentSessionBusyException("conv")
        val busyConversation = VoiceAgentConversation(runner, sessions, conversations, busy).bind("conv", selection)
        try {
            busyConversation.acquire()
            fail("Expected busy conversation failure")
        } catch (error: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.ConversationBusy, error.failure)
        }
    }

    @Test
    fun missingConversationSessionIsAPersistenceFailureAndReleasesTheLease() = runTest {
        val key = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, "conv")
        coEvery { sessions.getSession(key) } returns null
        try {
            conversation.acquire()
            fail("Expected persistence failure")
        } catch (error: VoiceAudioBackendException) {
            assertEquals(RealtimeVoiceFailure.Persistence, error.failure)
        }
        assertEquals(1, gate.releaseCount)
    }

    @Test
    fun releaseReleasesTheLeaseExactlyOnce() = runTest {
        acquiredCall()
        conversation.release()
        conversation.release()
        assertEquals(1, gate.releaseCount)
        // 没跑过任何话轮就不通知刷新，避免挂断即触发无意义的会话重载。
        coVerify(exactly = 0) { conversations.notifyConversationContentChanged(any()) }
    }

    @Test
    fun eachTurnNotifiesTheConversationRepositorySoTheChatScreenSeesNewMessages() = runTest {
        stubExecution(send = flowOf(modelText("已回复。", partial = true)))
        acquiredCall()
        conversation.runTurn("问题") { }
        coVerify(exactly = 1) { conversations.notifyConversationContentChanged("conv") }
        coVerify(exactly = 1) { conversations.refreshConversation("conv") }
    }
}
