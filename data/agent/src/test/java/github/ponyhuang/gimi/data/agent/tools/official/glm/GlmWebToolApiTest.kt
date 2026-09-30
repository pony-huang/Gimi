package github.ponyhuang.gimi.data.agent.tools.official.glm

import github.ponyhuang.gimi.data.agent.tools.official.cannedClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GLM Web 工具 HTTP 网关的 wire 行为;工具在会话中的解析与过滤
 * 由 [github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolsetTest] 覆盖。
 */
class GlmWebToolApiTest {

    @Test
    fun postsSearchRequestAndMapsResults() = runTest {
        var captured: okhttp3.Request? = null
        val api = GlmWebToolApi(
            apiKey = "secret",
            baseUrl = "",
            httpClient = cannedClient(200, SEARCH_BODY) { captured = it },
        )

        val results = api.search(
            query = "2026 人工智能趋势",
            count = 5,
            recencyFilter = "oneWeek",
            contentSize = "high",
        )

        val request = requireNotNull(captured)
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/web_search",
            request.url.toString(),
        )
        assertEquals("Bearer secret", request.header("Authorization"))
        assertEquals("POST", request.method)
        val payload = request.body!!.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }
        assertEquals(
            Json.parseToJsonElement("""{"search_query":"2026 人工智能趋势","search_engine":"search_std","search_intent":false,"count":5,"search_recency_filter":"oneWeek","content_size":"high"}"""),
            Json.parseToJsonElement(payload),
        )

        assertEquals(
            listOf(
                GlmSearchResult(
                    title = "示例标题",
                    link = "https://example.com/a",
                    content = "内容摘要",
                    media = "示例站点",
                    publishDate = "2026-07-01",
                ),
            ),
            results,
        )
    }

    @Test
    fun anthropicBaseUrlFallsBackToPaasEndpoint() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/web_search",
            GlmWebToolApi.webSearchUrl("https://open.bigmodel.cn/api/anthropic"),
        )
        assertEquals(
            "https://proxy.example.com/v4/web_search",
            GlmWebToolApi.webSearchUrl("https://proxy.example.com/v4/"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/reader",
            GlmWebToolApi.readerUrl("https://open.bigmodel.cn/api/anthropic"),
        )
        assertEquals(
            "https://proxy.example.com/v4/reader",
            GlmWebToolApi.readerUrl("https://proxy.example.com/v4/"),
        )
    }

    @Test
    fun postsReaderRequestAndMapsResult() = runTest {
        var captured: okhttp3.Request? = null
        val api = GlmWebToolApi(
            apiKey = "secret",
            baseUrl = "https://proxy.example.com/v4/",
            httpClient = cannedClient(200, READER_BODY) { captured = it },
        )

        val result = api.reader(
            url = "https://example.com/article",
            timeout = 15,
            noCache = true,
            returnFormat = "markdown",
        )

        val request = requireNotNull(captured)
        assertEquals("https://proxy.example.com/v4/reader", request.url.toString())
        assertEquals("Bearer secret", request.header("Authorization"))
        assertEquals("POST", request.method)
        val payload = request.body!!.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }
        assertEquals(
            Json.parseToJsonElement("""{"url":"https://example.com/article","timeout":15,"no_cache":true,"return_format":"markdown"}"""),
            Json.parseToJsonElement(payload),
        )
        assertEquals(
            GlmReaderResult(
                title = "示例文章",
                url = "https://example.com/article",
                description = "文章描述",
                content = "# 正文",
            ),
            result,
        )
    }

    @Test
    fun httpFailureThrowsWithResponseBody() = runTest {
        val api = GlmWebToolApi(
            apiKey = "secret",
            baseUrl = "",
            httpClient = cannedClient(500, """{"error":"boom"}"""),
        )

        val error = runCatching {
            api.search("query", count = null, recencyFilter = null, contentSize = null)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("HTTP 500"))
        assertTrue(error.message.orEmpty().contains("boom"))
    }

    private companion object {
        const val SEARCH_BODY = """
            {
              "id": "task-1",
              "created": 1780000000,
              "request_id": "req-1",
              "search_result": [
                {
                  "title": "示例标题",
                  "content": "内容摘要",
                  "link": "https://example.com/a",
                  "media": "示例站点",
                  "icon": "https://example.com/favicon.ico",
                  "refer": "[1]",
                  "publish_date": "2026-07-01"
                }
              ]
            }
        """

        const val READER_BODY = """
            {
              "id": "reader-1",
              "reader_result": {
                "content": "# 正文",
                "description": "文章描述",
                "title": "示例文章",
                "url": "https://example.com/article"
              }
            }
        """

    }
}
