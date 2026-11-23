package github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.HttpOptions
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAiWebSearchTest {
    @Test
    fun searchUsesOneCompleteResponseWithAnnotations() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"中🙂文","annotations":[{"type":"url_citation","url_citation":{"url":"https://example.org/post","title":"Post","start_index":0,"end_index":2}}]},"finish_reason":"stop"}]}""",
            ))
            val model = OpenAiCompatibleChatCompletions(
                name = "gpt-5-search-api",
                apiKey = "test-key",
                httpOptions = HttpOptions(baseUrl = server.url("/v1").toString()),
                webSearch = true,
            )
            val events: List<LlmResponse> = model.generateContent(
                LlmRequest(contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = "Search"))))),
                stream = true,
            ).toList()
            val sent = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val json = JSONObject(sent.body.readUtf8())
            assertFalse(json.optBoolean("stream"))
            assertEquals(0, json.getJSONObject("web_search_options").length())
            assertEquals(1, server.requestCount)
            val citation = WebCitationMetadata.decode(events.single().customMetadata?.get(WebCitationMetadata.KEY)).single()
            assertEquals(3, citation.endOffset)
            assertEquals("https://example.org/post", citation.source.url)
        }
    }
}
