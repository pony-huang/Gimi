package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmReaderTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebSearchTool
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局官方工具组装器:覆盖原各厂商 toolset 的行为 — 厂商声明解析、协议/服务隔离、
 * 模型家族门控、会话级工具与函数勾选、Gemini 原生工具与 ON_DEMAND 检索候选。
 */
class DefaultOfficialToolsetTest {

    // ---------------------------------------------------------------- 厂商声明

    @Test
    fun resolvesProviderDeclarationWithVendorWireName() = runTest {
        val tools = toolset().resolveTools(config(serviceId = "openai"), selection = null)

        // 目录 ID 是 openai_web_search,但线上声明字面量保持厂商规定的 web_search。
        assertEquals(listOf("web_search"), tools.map { it.name })
    }

    @Test
    fun ignoresServicesWithoutOfficialToolDeclarations() = runTest {
        assertTrue(
            toolset().resolveTools(config(serviceId = "deepseek"), selection = null).isEmpty(),
        )
    }

    @Test
    fun ignoresOpenaiServiceUsingAnthropicProtocol() = runTest {
        assertTrue(
            toolset()
                .resolveTools(config(serviceId = "openai", baseType = ApiProtocol.Anthropic), null)
                .isEmpty(),
        )
    }

    @Test
    fun resolvesWebSearchForAnthropicCompatibleVendors() = runTest {
        val toolset = toolset()
        for (serviceId in listOf("anthropic", "minimax")) {
            val tools = toolset.resolveTools(
                config(serviceId = serviceId, baseType = ApiProtocol.Anthropic),
                selection = null,
            )
            assertEquals("service: $serviceId", listOf("web_search"), tools.map { it.name })
        }
        for (serviceId in listOf("openai", "mimo")) {
            val tools = toolset.resolveTools(
                config(serviceId = serviceId, baseType = ApiProtocol.Standard),
                selection = null,
            )
            assertEquals("service: $serviceId", listOf("web_search"), tools.map { it.name })
        }
    }

    @Test
    fun dropsToolWhenConversationSelectionExcludesIt() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf("openai_web_search" to emptySet()),
        )

        assertTrue(
            toolset().resolveTools(config(serviceId = "openai"), selection).isEmpty(),
        )
    }

    // ---------------------------------------------------------------- Gemini

    @Test
    fun resolvesAllGeminiNativeToolsByDefault() = runTest {
        val tools = toolset().resolveTools(
            config(serviceId = "gemini", baseType = ApiProtocol.Gemini),
            selection = null,
        )

        assertEquals(3, tools.size)
        assertTrue(tools.any { it is GoogleSearchTool })
        assertTrue(tools.any { it is UrlContextTool })
        assertTrue(tools.any { it is GoogleMapsTool })
    }

    @Test
    fun ignoresGeminiServiceUsingNonGeminiProtocol() = runTest {
        assertTrue(
            toolset()
                .resolveTools(config(serviceId = "gemini", baseType = ApiProtocol.Standard), null)
                .isEmpty(),
        )
    }

    @Test
    fun dropsGeminiToolsExcludedByConversationSelection() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "gemini_web_search" to setOf("gemini_web_search"),
                "gemini_url_context" to emptySet(),
                "gemini_google_maps" to emptySet(),
            ),
        )

        val tools = toolset().resolveTools(
            config(serviceId = "gemini", baseType = ApiProtocol.Gemini),
            selection,
        )

        // web_search 分类对应 ADK 的 GoogleSearchTool(name = "google_search")。
        assertEquals(listOf("google_search"), tools.map { it.name })
        assertTrue(tools.single() is GoogleSearchTool)
    }

    // ---------------------------------------------------------------- GLM

    @Test
    fun glmToolsRequireModelFamilyMatch() = runTest {
        val toolset = toolset()

        assertEquals(
            listOf(GlmWebSearchTool.NAME, GlmReaderTool.NAME),
            toolset.resolveTools(config(serviceId = "glm", modelId = "glm-4.6"), null)
                .map { it.name },
        )
        assertTrue(
            toolset.resolveTools(config(serviceId = "glm", modelId = "other-model"), null)
                .isEmpty(),
        )
    }

    @Test
    fun glmToolsResolveForAnthropicProtocol() = runTest {
        val tools = toolset().resolveTools(
            config(serviceId = "glm", baseType = ApiProtocol.Anthropic, modelId = "glm-4.6"),
            selection = null,
        )

        assertEquals(2, tools.size)
    }

    @Test
    fun filtersIndividualGlmFunctionsFromConversationSelection() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "glm_web_search" to setOf(GlmReaderTool.NAME),
            ),
        )

        val tools = toolset().resolveTools(config(serviceId = "glm", modelId = "glm-4.6"), selection)

        assertEquals(listOf(GlmReaderTool.NAME), tools.map { it.name })
    }

    @Test
    fun glmSelectionIsKeyedByVendorUniqueToolId() = runTest {
        // 服务内未勾选 glm_web_search(即使其它工具勾选了)时不得注入。
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf("openai_web_search" to setOf("openai_web_search")),
        )

        assertTrue(
            toolset().resolveTools(config(serviceId = "glm", modelId = "glm-4.6"), selection).isEmpty(),
        )
    }

    @Test
    fun glmToolsUseApiKeyFromSecureServiceConfiguration() = runTest {
        var authorization: String? = null
        val client = cannedClient(200, """{"search_result":[]}""") { request ->
            authorization = request.header("Authorization")
        }
        val tool = toolset(httpClient = client, credential = "service-key")
            .resolveTools(config(serviceId = "glm", modelId = "glm-4.6"), selection = null)
            .single { it.name == GlmWebSearchTool.NAME }

        tool.run(mockk<ToolContext>(relaxed = true), mapOf("search_query" to "test"))

        assertEquals("Bearer service-key", authorization)
    }

    @Test
    fun glmToolsEmptyWithoutCredentials() = runTest {
        val registry = OfficialToolRegistry(
            kimiFormulaCache = testKimiFormulaCache(),
            httpClient = testHttpClient(),
            modelServices = emptyServices(),
        )

        assertTrue(
            DefaultOfficialToolset(registry)
                .resolveTools(config(serviceId = "glm", modelId = "glm-4.6"), selection = null)
                .isEmpty(),
        )
    }

    // ---------------------------------------------------------------- Kimi

    @Test
    fun kimiFormulasResolveFromRemoteManifest() = runTest {
        val tools = toolset(manifestClient(200, MANIFEST_BODY))
            .resolveTools(config(serviceId = "kimi", modelId = "kimi-k2.5"), selection = null)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun kimiFormulasRequireModelFamilyMatch() = runTest {
        assertTrue(
            toolset(manifestClient(200, MANIFEST_BODY))
                .resolveTools(config(serviceId = "kimi", modelId = "other-model"), null)
                .isEmpty(),
        )
    }

    @Test
    fun kimiFormulasFilteredByEnabledFunctionIds() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf("kimi_formulas" to setOf("missing_function")),
        )

        assertTrue(
            toolset(manifestClient(200, MANIFEST_BODY))
                .resolveTools(config(serviceId = "kimi", modelId = "kimi-k2.5"), selection)
                .isEmpty(),
        )
    }

    @Test
    fun kimiFormulasResolveAllWhenMarkerPresent() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "kimi_formulas" to setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            ),
        )

        val tools = toolset(manifestClient(200, MANIFEST_BODY))
            .resolveTools(config(serviceId = "kimi", modelId = "kimi-k2.5"), selection)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun kimiFormulasEmptyWhenManifestNetworkFails() = runTest {
        assertTrue(
            toolset(manifestClient(500, "{}"))
                .resolveTools(config(serviceId = "kimi", modelId = "kimi-k2.5"), selection = null)
                .isEmpty(),
        )
    }

    @Test
    fun kimiFormulasResolveForAnthropicProtocol() = runTest {
        val tools = toolset(manifestClient(200, MANIFEST_BODY))
            .resolveTools(
                config(serviceId = "kimi", baseType = ApiProtocol.Anthropic, modelId = "kimi-k2.5"),
                selection = null,
            )

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun searchCandidatesAreSkippedInOnDemandMode() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "kimi_formulas" to setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            ),
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        // ON_DEMAND 模式下标记为检索候选的声明不再直接注入,由 tool_search 按需暴露。
        assertTrue(
            toolset(manifestClient(200, MANIFEST_BODY))
                .resolveTools(config(serviceId = "kimi", modelId = "kimi-k2.5"), selection)
                .isEmpty(),
        )
    }

    @Test
    fun nonCandidateToolsStayDirectlyDeclaredInOnDemandMode() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "openai_web_search" to setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            ),
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        val tools = toolset().resolveTools(config(serviceId = "openai"), selection)

        assertEquals(listOf("web_search"), tools.map { it.name })
    }

    // ---------------------------------------------------------------- 目录

    @Test
    fun catalogListsStaticFunctions() = runTest {
        val registry = registry()

        assertEquals(
            listOf("openai_web_search"),
            registry.listFunctions("openai_web_search").map { it.id },
        )
        assertEquals(
            listOf(GlmWebSearchTool.NAME, GlmReaderTool.NAME),
            registry.listFunctions("glm_web_search").map { it.id },
        )
        assertTrue(registry.listFunctions("unknown_tool").isEmpty())
    }

    // ---------------------------------------------------------------- 夹具

    private fun toolset(
        httpClient: OkHttpClient = testHttpClient(),
        credential: String = "key",
    ): DefaultOfficialToolset = DefaultOfficialToolset(
        registry(httpClient, credential),
    )

    private fun registry(
        httpClient: OkHttpClient = testHttpClient(),
        credential: String = "key",
    ): OfficialToolRegistry = OfficialToolRegistry(
        kimiFormulaCache = testKimiFormulaCache(httpClient),
        httpClient = httpClient,
        modelServices = allServices(credential),
    )

    /** 同时提供 glm/kimi 等服务的凭据,便于各厂商用例共用一个注册表。 */
    private fun allServices(credential: String): AgentModelConfigurationSource {
        val serviceIds = listOf("openai", "anthropic", "minimax", "mimo", "gemini", "glm", "kimi")
        val services = serviceIds.map { serviceId ->
            mockk<LLMModelSetting> {
                every { id } returns serviceId
                every { isEnabled } returns true
                every { apiKey } returns credential
            }
        }
        return mockk {
            every { currentServices() } returns services
        }
    }

    private fun manifestClient(code: Int, body: String): OkHttpClient =
        cannedClient(code, body) { _: Request -> }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Standard,
        modelId: String = "model",
    ) = ModelRuntimeMetadata(
        serviceId = serviceId,
        baseType = baseType,
        modelId = modelId,
        fullBaseUrl = "https://example.com",
    )

    private companion object {
        const val MANIFEST_BODY =
            """{"tools":[{"function":{"name":"translate","description":"Translate text"}}]}"""
    }
}
