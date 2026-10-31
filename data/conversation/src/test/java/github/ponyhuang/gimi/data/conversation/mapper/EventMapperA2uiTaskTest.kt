package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/** 历史恢复必须与实时归约保持同一占位归属，且不能在重启后留下永久 loading。 */
class EventMapperA2uiTaskTest {
    private val task = A2uiGenerationTask("call", "surface", "invocation", "image facts", setOf("Image"))

    @Test
    fun completionReplacesOriginalPlaceholderAndPreservesFollowingProse() {
        val document = A2uiDocument(task.surfaceId, emptyList())
        val pending = Event(id = "placeholder", author = "Assistant", content = Content(role = Role.MODEL,
            parts = listOf(Part(functionResponse = FunctionResponse(id = task.callId, name = A2uiToolResult.TOOL_NAME,
                response = result(task))))))
        val prose = Event(id = "prose", author = "Assistant", content = Content(role = Role.MODEL,
            parts = listOf(Part(text = "answer"))))
        val complete = Event(author = A2uiGenerationTaskCodec.AUTHOR,
            content = Content(role = Role.SYSTEM, parts = listOf(Part(text = "internal status"))),
            customMetadata = mapOf(A2uiGenerationTaskCodec.RESULT_KEY to
                (result(task.copy(status = A2uiGenerationStatus.COMPLETED)) +
                    (A2uiToolResult.DOCUMENT_KEY to A2uiToolResult.encode(document)))))
        val session = mockk<Session> { every { events } returns mutableListOf(pending, prose, complete) }

        val messages = EventMapper.fromSession(session)

        assertEquals(listOf("placeholder", "prose"), messages.map { it.id })
        assertEquals(document, messages.first().functionResponses.single().a2uiDocument)
        assertEquals("answer", messages.last().textParts.single().text)
    }

    @Test
    fun uncompletedTaskIsInterruptedOnHistoryReload() {
        val pending = Event(author = "Assistant", content = Content(role = Role.MODEL,
            parts = listOf(Part(functionResponse = FunctionResponse(id = task.callId, name = A2uiToolResult.TOOL_NAME,
                response = result(task))))))
        val session = mockk<Session> { every { events } returns mutableListOf(pending) }

        val recovered = EventMapper.fromSession(session).single().functionResponses.single().a2uiTask!!

        assertEquals(A2uiGenerationStatus.CANCELLED, recovered.status)
        assertEquals(task.changes, recovered.changes)
    }

    private fun result(snapshot: A2uiGenerationTask): Map<String, Any> = mapOf(
        "success" to true, A2uiGenerationTaskCodec.KEY to A2uiGenerationTaskCodec.encode(snapshot),
    )
}
