package github.ponyhuang.gimi.data.agent.conversation

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatRunner
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `AdkChatAgentRepository` 的 ADK Event → ChatRunEvent 映射 characterization。
 *
 * 固定当前外部可见行为：固定 user id 透传、part/function call/confirmation 映射，
 * 防止后续重构（模块迁移、契约调整）改变行为。
 */
class AdkChatAgentRepositoryMappingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val execution = mockk<AgentChatRunner.Execution>()
    private val runner = mockk<AgentChatRunner> {
        coEvery { createExecution(any(), any(), any(), any(), any()) } returns execution
    }
    private val repository = AdkChatAgentRepository(runner)
    private val selection = ModelSelection(serviceId = "svc", groupId = "grp", modelId = "mdl")

    @Test
    fun sendDelegatesWithFixedUserIdAndMapsEvent() = runTest {
        val event = adkEvent(
            parts = listOf(Part(text = "你好")),
            calls = listOf(FunctionCall(id = "call-1", name = "clock", args = mapOf("zone" to "UTC"))),
            responses = listOf(
                FunctionResponse(
                    id = "call-1",
                    name = "search_media_files",
                    response = localFileResponse(),
                ),
            ),
            isPartial = false,
            isTurnComplete = true,
        )
        coEvery {
            execution.send(any(), any(), any())
        } returns flowOf(event)

        val events = repository.createExecution("session-1", selection).send("现在几点", emptyList()).toList()

        coVerify {
            runner.createExecution(
                userId = AgentSessionIdentity.DEFAULT_USER_ID,
                sessionId = "session-1",
                selection = selection,
                toolConfiguration = null,
            )
            execution.send("现在几点", emptyList())
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
        assertEquals("search_media_files", mapped.functionResponses.single().name)
        assertEquals(
            "photo.jpg",
            mapped.functionResponses.single().localFileSearchResult?.files?.single()?.displayName,
        )
        assertFalse(mapped.partial)
        assertTrue(mapped.turnComplete)
        assertNull(mapped.errorCode)
        assertNull(mapped.errorMessage)
        assertEquals(123L, mapped.timestamp)
    }

    @Test
    fun retryDelegatesStableInvocationIdsToTheAdkRunner() = runTest {
        coEvery {
            execution.send(any(), any(), any())
        } returns flowOf(adkEvent())

        repository.createExecution("session-1", selection).send(
            text = "retry",
            fileAttachments = emptyList(),
            rewindBeforeInvocationId = "attempt-1",
        ).toList()

        coVerify {
            execution.send(
                text = "retry",
                fileAttachments = emptyList(),
                rewindBeforeInvocationId = "attempt-1",
            )
        }
    }

    @Test
    fun mapsConfirmationFunctionCallToToolConfirmationRequest() = runTest {
        val confirmationCall = FunctionCall(
            id = "confirm-1",
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
            args = mapOf(
                "originalFunctionCall" to mapOf(
                    "name" to "brightness_set",
                    "id" to "brightness-call-1",
                    "args" to mapOf("level" to 80),
                ),
            ),
        )
        coEvery {
            execution.respondToToolConfirmation(any(), any())
        } returns flowOf(adkEvent(calls = listOf(confirmationCall)))

        val events = repository.createExecution("session-1", selection)
            .respondToToolConfirmation("confirm-1", confirmed = true)
            .toList()

        coVerify {
            execution.respondToToolConfirmation(
                confirmationCallId = "confirm-1",
                confirmed = true,
            )
        }
        val confirmation = events.single().functionCalls.single().confirmationRequest
        assertEquals("brightness-call-1", confirmation?.originalCallId)
        assertEquals("brightness_set", confirmation?.toolName)
        assertEquals(mapOf("level" to 80), confirmation?.args)
    }

    @Test
    fun mapsConfirmationResponseDecision() = runTest {
        val response = FunctionResponse(
            id = "confirm-1",
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
            response = mapOf("confirmed" to false),
        )
        coEvery {
            execution.respondToToolConfirmation(any(), any())
        } returns flowOf(adkEvent(responses = listOf(response)))

        val mapped = repository.createExecution("session-1", selection)
            .respondToToolConfirmation("confirm-1", confirmed = false)
            .toList()
            .single()
            .functionResponses
            .single()

        assertEquals(false, mapped.confirmationApproved)
    }

    @Test
    fun streamedFileDataWithDeletedPayloadMapsToMissingPlaceholder() = runTest {
        val gone = temporaryFolder.newFile("report.pdf").apply { delete() }
        val event = adkEvent(
            parts = listOf(
                Part(
                    fileData = FileData(
                        mimeType = "application/pdf",
                        displayName = "report.pdf",
                        fileUri = gone.absolutePath,
                    ),
                ),
            ),
        )
        coEvery {
            execution.send(any(), any(), any())
        } returns flowOf(event)

        val mapped = repository.createExecution("session-1", selection)
            .send("继续", emptyList())
            .toList()

        val attachment = mapped.single().parts.single().attachment
        assertEquals(true, attachment?.isMissing)
        assertEquals("report.pdf", attachment?.displayName)
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

    private fun localFileResponse(): Map<String, Any?> = mapOf(
        "result" to mapOf(
            "success" to true,
            "query" to "photo",
            "results" to listOf(
                mapOf(
                    "displayName" to "photo.jpg",
                    "mimeType" to "image/jpeg",
                    "sizeBytes" to 1L,
                    "modifiedTimeMillis" to 2L,
                    "category" to "image",
                    "contentUri" to "content://media/photo",
                ),
            ),
        ),
    )
}
