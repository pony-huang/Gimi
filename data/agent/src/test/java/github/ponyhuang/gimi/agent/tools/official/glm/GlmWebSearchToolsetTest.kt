package github.ponyhuang.gimi.agent.tools.official.glm

import github.ponyhuang.gimi.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.agent.tools.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.gimi.agent.tools.official.KimiFormulaCatalog
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
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
    fun notApplicableWhenModelIsNotGlm() = runTest {
        val toolset = toolset()
        val config = config(modelId = "other-model")

        assertTrue(toolset.resolveTools(config, selection = null).isEmpty())
    }

    @Test
    fun notApplicableWhenConversationSelectionExcludesIt() = runTest {
        val toolset = toolset()
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "glm" to mapOf(GlmWebSearchToolset.TOOL_ID to emptySet()),
            ),
        )

        assertTrue(toolset.resolveTools(config(), selection).isEmpty())
    }

    @Test
    fun resolvesWebSearchAndReaderTools() = runTest {
        val toolset = toolset()

        val tools = toolset.resolveTools(config(), selection = null)

        assertEquals(
            listOf(GlmWebSearchTool.NAME, GlmReaderTool.NAME),
            tools.map { it.name },
        )
        assertEquals(
            listOf("search_query"),
            tools.first().declaration()!!.parameters?.required,
        )
        assertEquals(
            listOf("url"),
            tools.last().declaration()!!.parameters?.required,
        )
    }

    @Test
    fun filtersIndividualGlmFunctionsFromConversationSelection() = runTest {
        val toolset = toolset()
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "glm" to mapOf(
                    GlmWebSearchToolset.TOOL_ID to setOf(GlmReaderTool.NAME),
                ),
            ),
        )

        val tools = toolset.resolveTools(config(), selection)

        assertEquals(listOf(GlmReaderTool.NAME), tools.map { it.name })
    }

    @Test
    fun officialCatalogListsSearchAndReaderFunctions() = runTest {
        val catalog = DefaultOfficialToolFunctionCatalog(mockk<KimiFormulaCatalog>())

        val functions = catalog.listFunctions(GlmWebSearchToolset.TOOL_ID)

        assertEquals(
            listOf(GlmWebSearchTool.NAME, GlmReaderTool.NAME),
            functions.map { it.id },
        )
    }

    @Test
    fun resolvesForAnthropicProtocolConfigs() = runTest {
        val toolset = toolset()
        val config = config(baseType = ApiProtocol.Anthropic)

        assertEquals(2, toolset.resolveTools(config, selection = null).size)
    }

    private fun config(
        modelId: String = "glm-4.6",
        baseType: ApiProtocol = ApiProtocol.Standard,
    ) = ModelRuntimeMetadata(
        serviceId = "glm",
        baseType = baseType,
        modelId = modelId,
        fullBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
    )

    private fun toolset(
        httpClient: OkHttpClient = OkHttpClient(),
        credential: String = "key",
    ): GlmWebSearchToolset {
        val service = mockk<LLMModelSetting> {
            every { id } returns "glm"
            every { isEnabled } returns true
            every { apiKey } returns credential
        }
        val modelServices = mockk<AgentModelConfigurationSource> {
            every { currentServices() } returns listOf(service)
        }
        return GlmWebSearchToolset(httpClient, modelServices)
    }
}

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
