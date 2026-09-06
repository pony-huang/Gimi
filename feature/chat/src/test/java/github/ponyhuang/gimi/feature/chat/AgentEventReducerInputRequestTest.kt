package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventReducerInputRequestTest {

    private val runtime = ChatSessionRuntime("session")

    private fun reducer() = AgentEventReducer(
        runtimeOrNull = { if (it == runtime.sessionId) runtime else null },
        runtimeFor = { runtime },
        publishRuntime = {},
        emitPartDelta = { _, _, _ -> },
        scope = CoroutineScope(StandardTestDispatcher()),
        repository = mockk<ConversationRepository>(relaxed = true),
        toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true),
        isAutoApproved = { false },
    )

    private fun inputCallEvent(callId: String, toolName: String = "get_user_choice") = ChatRunEvent(
        id = "event-$callId",
        invocationId = "invocation",
        author = "assistant",
        parts = emptyList(),
        functionCalls = listOf(
            ChatFunctionCall(
                id = callId,
                name = toolName,
                args = emptyMap(),
                inputRequest = UserInputRequest(
                    callId = callId,
                    toolName = toolName,
                    kind = if (toolName == "get_user_choice") UserInputKind.CHOICE else UserInputKind.FREE_TEXT,
                    message = "选一个",
                    options = listOf("A", "B"),
                ),
            ),
        ),
        functionResponses = emptyList(),
        partial = false,
        turnComplete = false,
        errorCode = null,
        errorMessage = null,
        timestamp = 0L,
    )

    @Test
    fun inputRequestCallIsCapturedAndSessionWaitsForInput() = runTest {
        val reducer = reducer()
        val runToken = Any()
        runtime.runToken = runToken

        reducer.applyEvent(runtime.sessionId, inputCallEvent("call-1"), runToken)

        assertEquals(1, runtime.pendingInputRequests.size)
        assertEquals("call-1", runtime.pendingInputRequests.first().callId)
        assertTrue(runtime.isActive)
        assertEquals(ConversationTaskStatus.WaitingForInput, runtime.drawerStatus())
        assertEquals(AgentTaskPhase.WAITING_FOR_INPUT, runtime.phase)
    }

    @Test
    fun duplicateCallIdIsNotCapturedTwice() = runTest {
        val reducer = reducer()
        val runToken = Any()
        runtime.runToken = runToken

        reducer.applyEvent(runtime.sessionId, inputCallEvent("call-1"), runToken)
        reducer.applyEvent(runtime.sessionId, inputCallEvent("call-1"), runToken)

        assertEquals(1, runtime.pendingInputRequests.size)
    }

    @Test
    fun staleRunTokenCannotCaptureInputRequest() = runTest {
        val reducer = reducer()

        reducer.applyEvent(runtime.sessionId, inputCallEvent("call-1"), runToken = Any())

        assertFalse(runtime.pendingInputRequests.isNotEmpty())
    }

    @Test
    fun unrelatedEventDoesNotCaptureAnything() = runTest {
        val reducer = reducer()
        val runToken = Any()
        runtime.runToken = runToken
        val plainEvent = ChatRunEvent(
            id = "event-text",
            invocationId = "invocation",
            author = "assistant",
            parts = emptyList(),
            functionCalls = listOf(ChatFunctionCall(id = "call-9", name = "web_search", args = emptyMap())),
            functionResponses = emptyList(),
            partial = false,
            turnComplete = true,
            errorCode = null,
            errorMessage = null,
            timestamp = 0L,
        )

        reducer.applyEvent(runtime.sessionId, plainEvent, runToken)

        assertFalse(runtime.pendingInputRequests.isNotEmpty())
    }
}
