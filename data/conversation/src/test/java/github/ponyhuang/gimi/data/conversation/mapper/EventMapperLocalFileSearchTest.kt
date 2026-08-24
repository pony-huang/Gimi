package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Role
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMapperLocalFileSearchTest {

    @Test
    fun `restores structured local files from persisted function response`() {
        val event = mockk<Event> {
            every { id } returns "event-1"
            every { invocationId } returns "invocation-1"
            every { author } returns "assistant"
            every { content } returns Content(role = Role.MODEL, parts = emptyList())
            every { functionCalls() } returns emptyList()
            every { functionResponses() } returns listOf(
                FunctionResponse(
                    id = "call-1",
                    name = "search_documents",
                    response = mapOf(
                        "success" to true,
                        "query" to "report",
                        "results" to listOf(
                            mapOf(
                                "displayName" to "report.pdf",
                                "mimeType" to "application/pdf",
                                "sizeBytes" to 10L,
                                "modifiedTimeMillis" to 20L,
                                "category" to "document",
                                "contentUri" to "content://documents/report",
                            ),
                        ),
                    ),
                ),
            )
            every { partial } returns false
            every { turnComplete } returns false
            every { errorCode } returns null
            every { errorMessage } returns null
            every { timestamp } returns 123L
        }

        val result = EventMapper.fromEvent(event)

        assertEquals(
            "report.pdf",
            result?.functionResponses?.single()?.localFileSearchResult?.files?.single()?.displayName,
        )
    }
}
