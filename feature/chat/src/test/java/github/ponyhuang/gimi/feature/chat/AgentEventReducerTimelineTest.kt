package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.AdkRequestConfirmationToolName
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
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
    ) = ChatRunEvent(
        id = id,
        invocationId = "invocation",
        author = "assistant",
        parts = text?.let { listOf(ChatRunPart(text = it)) }.orEmpty(),
        functionCalls = functionCalls,
        functionResponses = emptyList(),
        partial = partial,
        turnComplete = !partial,
        errorCode = null,
        errorMessage = null,
        timestamp = timestamp,
    )
}
