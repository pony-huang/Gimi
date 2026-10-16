package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AdkRequestConfirmationToolName
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentEventReducerTimelineTest {
    @Test
    fun `one merged event completes every parallel tool response`() {
        val runToken = Any()
        runtime.runToken = runToken
        reducer.applyEvent(
            runtime.sessionId,
            event(
                functionCalls = listOf(
                    ChatFunctionCall(id = "call-a", name = "alpha", args = emptyMap()),
                    ChatFunctionCall(id = "call-b", name = "beta", args = emptyMap()),
                ),
            ),
            runToken,
        )
        assertEquals(ToolCallStatus.Running, runtime.toolStatuses[ToolCallKey("call-a", "alpha")])
        assertEquals(ToolCallStatus.Running, runtime.toolStatuses[ToolCallKey("call-b", "beta")])

        // ADK 1.4 把同一批并发工具的响应合并进一个事件。
        reducer.applyEvent(
            runtime.sessionId,
            event(
                functionResponses = listOf(
                    ChatFunctionResponse(id = "call-a", name = "alpha"),
                    ChatFunctionResponse(id = "call-b", name = "beta"),
                ),
            ),
            runToken,
        )

        assertEquals(ToolCallStatus.Completed, runtime.toolStatuses[ToolCallKey("call-a", "alpha")])
        assertEquals(ToolCallStatus.Completed, runtime.toolStatuses[ToolCallKey("call-b", "beta")])
    }

    @Test
    fun `error event and subsequent execution exception produce one error bubble`() {
        val token = Any()
        runtime.runToken = token
        reducer.applyEvent(runtime.sessionId, event().copy(errorCode = "MODEL_FAILURE"), token)
        reducer.applyError(runtime.sessionId, "MODEL_FAILURE")
        assertEquals(1, runtime.messages.size)
        assertEquals("MODEL_FAILURE", runtime.messages.single().error)
        assertEquals("invocation", runtime.messages.single().invocationId)
        assertFalse(runtime.isAgentRunning)
    }

    @Test
    fun `a new running attempt retains its own identical error`() {
        reducer.applyError(runtime.sessionId, "MODEL_FAILURE", "old")
        runtime.isAgentRunning = true
        reducer.applyError(runtime.sessionId, "MODEL_FAILURE", "new")
        assertEquals(2, runtime.messages.size)
    }
    @Test
    fun `same event id across chunks keeps one bubble without duplicating final text`() {
        val runToken = Any()
        runtime.runToken = runToken
        reducer.applyEvent(runtime.sessionId, event(id = "stream", partial = true, text = "a"), runToken)
        val partId = runtime.messages.single().textParts.single().id
        reducer.applyEvent(runtime.sessionId, event(id = "stream", partial = true, text = "b"), runToken)
        reducer.applyEvent(runtime.sessionId, event(id = "stream", text = "ab"), runToken)
        val message = runtime.messages.single()
        assertEquals("stream", message.id)
        assertEquals("ab", message.textParts.single().text)
        assertEquals(partId, message.textParts.single().id)
        assertFalse(message.partial)
    }

    private val runtime = ChatSessionRuntime("session")
    private val reducer = AgentEventReducer(
        runtimeOrNull = { if (it == runtime.sessionId) runtime else null },
        runtimeFor = { runtime },
        publishRuntime = {},
        emitPartDelta = { _, _, _ -> },
        toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true) {
            every { tools } returns MutableStateFlow(emptyList())
        },
        isAutoApproved = { false },
    )

    @Test
    fun `partial final merge keeps ids and advances timestamp`() {
        val runToken = Any()
        runtime.runToken = runToken
        reducer.applyEvent(
            runtime.sessionId,
            event(id = "partial", partial = true, timestamp = 1_000L, text = "answer"),
            runToken,
        )
        val partId = runtime.messages.single().textParts.single().id

        reducer.applyEvent(
            runtime.sessionId,
            event(id = "final", partial = false, timestamp = 3_000L, text = "answer"),
            runToken,
        )

        val message = runtime.messages.single()
        assertEquals("partial", message.id)
        assertEquals(partId, message.textParts.single().id)
        assertEquals(3_000L, message.timestamp)
        assertFalse(message.partial)
    }

    @Test
    fun `confirmation request tracks awaiting state by original call id`() {
        val runToken = Any()
        runtime.runToken = runToken
        val confirmation = ChatFunctionCall(
            id = "confirm-1",
            name = AdkRequestConfirmationToolName,
            args = emptyMap(),
            confirmationRequest = ToolConfirmationRequest(
                originalCallId = "original-1",
                toolName = "delete_file",
                args = emptyMap(),
            ),
        )

        reducer.applyEvent(
            runtime.sessionId,
            event(functionCalls = listOf(confirmation)),
            runToken,
        )

        assertEquals("original-1", runtime.pendingToolConfirmations.single().originalCallId)
        assertEquals(
            ToolCallStatus.AwaitingConfirmation,
            runtime.toolStatuses[ToolCallKey("original-1", "delete_file")],
        )
    }

    private fun event(
        id: String = "event",
        partial: Boolean = false,
        timestamp: Long = 1L,
        text: String? = null,
        functionCalls: List<ChatFunctionCall> = emptyList(),
        functionResponses: List<ChatFunctionResponse> = emptyList(),
    ) = ChatRunEvent(
        id = id,
        invocationId = "invocation",
        author = "assistant",
        parts = text?.let { listOf(ChatRunPart(text = it)) }.orEmpty(),
        functionCalls = functionCalls,
        functionResponses = functionResponses,
        partial = partial,
        turnComplete = !partial,
        errorCode = null,
        errorMessage = null,
        timestamp = timestamp,
    )
}
