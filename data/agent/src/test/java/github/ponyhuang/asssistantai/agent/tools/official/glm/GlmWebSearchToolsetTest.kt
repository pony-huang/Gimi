package github.ponyhuang.asssistantai.agent.tools.official.glm

import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmWebSearchToolsetTest {

    @Test
    fun notApplicableWhenGlmWebSearchNotEnabled() = runTest {
        val toolset = GlmWebSearchToolset(OkHttpClient())
        val config = config(officialTools = emptyList())

        assertTrue(toolset.resolveTools(config, selection = null).isEmpty())
    }

    @Test
    fun notApplicableWhenConversationSelectionExcludesIt() = runTest {
        val toolset = GlmWebSearchToolset(OkHttpClient())
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "glm" to mapOf(OfficialToolIds.GLM_WEB_SEARCH to emptySet()),
            ),
        )

        assertTrue(toolset.resolveTools(config(), selection).isEmpty())
    }

    @Test
    fun resolvesSingleWebSearchTool() = runTest {
        val toolset = GlmWebSearchToolset(OkHttpClient())

        val tools = toolset.resolveTools(config(), selection = null)

        assertEquals(listOf(GlmWebSearchTool.NAME), tools.map { it.name })
        assertEquals(
            listOf("search_query"),
            tools.single().declaration()!!.parameters?.required,
        )
    }

    @Test
    fun resolvesForAnthropicProtocolConfigs() = runTest {
        val toolset = GlmWebSearchToolset(OkHttpClient())
        val config = config(baseType = ApiProtocol.Anthropic)

        assertEquals(1, toolset.resolveTools(config, selection = null).size)
    }

    private fun config(
        officialTools: List<String> = listOf(OfficialToolIds.GLM_WEB_SEARCH),
        baseType: ApiProtocol = ApiProtocol.Standard,
    ) = ModelConfig(
        serviceId = "glm",
        baseType = baseType,
        modelId = "glm-4.6",
        apiKey = "key",
        fullBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        officialTools = officialTools,
    )
}

class GlmWebSearchApiTest {

    @Test
    fun postsSearchRequestAndMapsResults() = runTest {
        var captured: okhttp3.Request? = null
        val api = GlmWebSearchApi(
            apiKey = "secret",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
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
            GlmWebSearchApi.webSearchUrl("https://open.bigmodel.cn/api/anthropic"),
        )
        assertEquals(
            "https://proxy.example.com/v4/web_search",
            GlmWebSearchApi.webSearchUrl("https://proxy.example.com/v4/"),
        )
    }

    @Test
    fun httpFailureThrowsWithResponseBody() = runTest {
        val api = GlmWebSearchApi(
            apiKey = "secret",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
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
