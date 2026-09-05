package github.ponyhuang.gimi.data.agent.tools.official.glm

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        val payload = request.body!!.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }
        assertTrue(payload.contains("\"search_query\":\"2026 人工智能趋势\""))
        assertTrue(payload.contains("\"search_engine\":\"search_std\""))
        assertTrue(payload.contains("\"search_intent\":false"))
        assertTrue(payload.contains("\"count\":5"))
        assertTrue(payload.contains("\"search_recency_filter\":\"oneWeek\""))
        assertTrue(payload.contains("\"content_size\":\"high\""))

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
        val payload = request.body!!.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }
        assertTrue(payload.contains("\"url\":\"https://example.com/article\""))
        assertTrue(payload.contains("\"timeout\":15"))
        assertTrue(payload.contains("\"no_cache\":true"))
        assertTrue(payload.contains("\"return_format\":\"markdown\""))
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

        /** Short-circuits every request with a canned response. */
        fun cannedClient(
            code: Int,
            body: String,
            onRequest: (okhttp3.Request) -> Unit = {},
        ): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    onRequest(chain.request())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("canned")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
    }
}
