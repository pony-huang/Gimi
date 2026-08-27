package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.events.Event
import com.google.adk.kt.serialization.adkJson
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
                        "result" to mapOf(
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

    @Test
    fun `restores structured remote images from persisted function response`() {
        val event = mockk<Event> {
            every { id } returns "event-images"
            every { invocationId } returns "invocation-images"
            every { author } returns "assistant"
            every { content } returns Content(role = Role.MODEL, parts = emptyList())
            every { functionCalls() } returns emptyList()
            every { functionResponses() } returns listOf(
                FunctionResponse(
                    id = "call-images",
                    name = "get_feed_detail",
                    response = mapOf(
                        "data" to mapOf(
                            "note" to mapOf(
                                "imageList" to listOf(
                                    mapOf("urlDefault" to "https://images.example.com/photo"),
                                ),
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
            "https://images.example.com/photo",
            result?.functionResponses?.single()?.remoteImageResult?.images?.single()?.url,
        )
    }

    @OptIn(FrameworkInternalApi::class)
    @Test
    fun `restores remote images after ADK JSON persistence round trip`() {
        val event = adkJson.decodeFromString<Event>(
            """
            {
              "id":"event-persisted-images",
              "author":"Assistant",
              "content":{
                "role":"user",
                "parts":[{
                  "functionResponse":{
                    "name":"get_feed_detail",
                    "id":"call-persisted-images",
                    "response":{
                      "data":{
                        "note":{
                          "imageList":[{
                            "urlDefault":"http://images.example.com/photo",
                            "width":1242,
                            "height":1656
                          }]
                        }
                      }
                    }
                  }
                }]
              }
            }
            """.trimIndent(),
        )

        val result = EventMapper.fromEvent(event)

        assertEquals(
            "https://images.example.com/photo",
            result?.functionResponses?.single()?.remoteImageResult?.images?.single()?.url,
        )
    }
}
