package github.ponyhuang.gimi.agent.conversation

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AdkChatAgentRepository` 的 ADK Event → ChatRunEvent 映射 characterization。
 *
 * 固定当前外部可见行为：固定 user id 透传、part/function call/confirmation 映射，
 * 防止后续重构（模块迁移、契约调整）改变行为。
 */
class AdkChatAgentRepositoryMappingTest {

    private val runner = mockk<AgentChatRunner>()
    private val repository = AdkChatAgentRepository(runner)
    private val selection = ModelSelection(serviceId = "svc", groupId = "grp", modelId = "mdl")

    @Test
    fun sendDelegatesWithFixedUserIdAndMapsEvent() = runTest {
        val event = adkEvent(
            parts = listOf(Part(text = "你好")),
            calls = listOf(FunctionCall(id = "call-1", name = "clock", args = mapOf("zone" to "UTC"))),
            responses = listOf(FunctionResponse(id = "call-1", name = "clock", response = emptyMap())),
            isPartial = false,
            isTurnComplete = true,
        )
        coEvery { runner.send(any(), any(), any(), any(), any(), any()) } returns flowOf(event)

        val events = repository.send("session-1", selection, "现在几点", emptyList(), null).toList()

        coVerify {
            runner.send(
                userId = AgentSessionIdentity.DEFAULT_USER_ID,
                sessionId = "session-1",
                selection = selection,
                text = "现在几点",
                fileAttachments = emptyList(),
                toolConfiguration = null,
            )
        }
        val mapped = events.single()
        assertEquals("evt-1", mapped.id)
        assertEquals("inv-1", mapped.invocationId)
        assertEquals("assistant", mapped.author)
        assertEquals("你好", mapped.parts.single().text)
        assertFalse(mapped.parts.single().thought)
        assertNull(mapped.parts.single().attachment)
        assertEquals("call-1", mapped.functionCalls.single().id)
        assertEquals("clock", mapped.functionCalls.single().name)
        assertEquals(mapOf("zone" to "UTC"), mapped.functionCalls.single().args)
        assertNull(mapped.functionCalls.single().confirmationRequest)
        assertEquals("call-1", mapped.functionResponses.single().id)
        assertEquals("clock", mapped.functionResponses.single().name)
        assertFalse(mapped.partial)
        assertTrue(mapped.turnComplete)
        assertNull(mapped.errorCode)
        assertNull(mapped.errorMessage)
        assertEquals(123L, mapped.timestamp)
    }

    @Test
    fun mapsConfirmationFunctionCallToToolConfirmationRequest() = runTest {
        val confirmationCall = FunctionCall(
            id = "confirm-1",
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
            args = mapOf(
                FunctionCall.ORIGINAL_FUNCTION_CALL_KEY to mapOf(
                    "name" to "brightness_set",
                    "args" to mapOf("level" to 80),
                ),
            ),
        )
        coEvery {
            runner.respondToToolConfirmation(any(), any(), any(), any())
        } returns flowOf(adkEvent(calls = listOf(confirmationCall)))

        val events = repository
            .respondToToolConfirmation("session-1", "confirm-1", confirmed = true)
            .toList()

        coVerify {
            runner.respondToToolConfirmation(
                userId = AgentSessionIdentity.DEFAULT_USER_ID,
                sessionId = "session-1",
                confirmationCallId = "confirm-1",
                confirmed = true,
            )
        }
        val confirmation = events.single().functionCalls.single().confirmationRequest
        assertEquals("brightness_set", confirmation?.toolName)
        assertEquals(mapOf("level" to 80), confirmation?.args)
    }

    @Test
    fun releaseSessionDelegatesToRunner() = runTest {
        coEvery { runner.releaseSession(any()) } returns Unit

        repository.releaseSession("session-9")

        coVerify { runner.releaseSession("session-9") }
    }

    private fun adkEvent(
        parts: List<Part> = emptyList(),
        calls: List<FunctionCall> = emptyList(),
        responses: List<FunctionResponse> = emptyList(),
        isPartial: Boolean = false,
        isTurnComplete: Boolean = false,
    ): Event = mockk {
        every { id } returns "evt-1"
        every { invocationId } returns "inv-1"
        every { author } returns "assistant"
        every { content } returns Content(role = Role.MODEL, parts = parts)
        every { functionCalls() } returns calls
        every { functionResponses() } returns responses
        every { partial } returns isPartial
        every { turnComplete } returns isTurnComplete
        every { errorCode } returns null
        every { errorMessage } returns null
        every { timestamp } returns 123L
    }
}
