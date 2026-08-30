package github.ponyhuang.gimi.data.agent.tools.official.kimi

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
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

class KimiFormulaToolsetTest {

    @Test
    fun notApplicableWhenModelIsNotKimi() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))
        val config = config(modelId = "other-model")

        assertTrue(toolset.resolveTools(config, selection = null).isEmpty())
    }

    @Test
    fun emptyWhenToolAbsentFromConversationSelection() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                SERVICE_ID to mapOf("web_search" to setOf("web_search")),
            ),
        )

        assertTrue(toolset.resolveTools(config(), selection).isEmpty())
    }

    @Test
    fun filteredByEnabledFunctionIds() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))
        val selection = selection(KimiFormulaToolset.TOOL_ID to setOf("translate"))

        val tools = toolset.resolveTools(config(), selection)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun emptyWhenEnabledFunctionIdsMatchNothing() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))
        val selection = selection(KimiFormulaToolset.TOOL_ID to setOf("missing_function"))

        assertTrue(toolset.resolveTools(config(), selection).isEmpty())
    }

    @Test
    fun allFunctionsWhenMarkerPresent() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))
        val selection = selection(
            KimiFormulaToolset.TOOL_ID to
                    setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
        )

        val tools = toolset.resolveTools(config(), selection)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun allFunctionsWhenNoConversationConfig() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))

        val tools = toolset.resolveTools(config(), selection = null)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun manifestEmptyWhenNetworkFails() = runTest {
        val toolset = toolset(manifestClient(500, "{}"))

        assertTrue(toolset.resolveTools(config(), selection = null).isEmpty())
    }

    @Test
    fun usesApiKeyFromSecureServiceConfiguration() = runTest {
        var authorization: String? = null
        val toolset = toolset(
            manifestClient(200, MANIFEST_BODY) { request ->
                authorization = request.header("Authorization")
            },
            credential = "service-key",
        )

        toolset.resolveTools(config(), selection = null)

        assertEquals("Bearer service-key", authorization)
    }

    @Test
    fun resolvesForAnthropicProtocol() = runTest {
        val toolset = toolset(manifestClient(200, MANIFEST_BODY))

        val tools = toolset.resolveTools(
            config(baseType = ApiProtocol.Anthropic),
            selection = null,
        )

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    private fun config(
        modelId: String = "kimi-k2.5",
        baseType: ApiProtocol = ApiProtocol.Standard,
    ) = ModelRuntimeMetadata(
        serviceId = SERVICE_ID,
        baseType = baseType,
        modelId = modelId,
        fullBaseUrl = "https://example.com",
    )

    private fun selection(
        vararg functionsByTool: Pair<String, Set<String>>,
    ) = ConversationToolConfiguration(
        enabledOfficialFunctionIdsByService = mapOf(SERVICE_ID to mapOf(*functionsByTool)),
    )

    private fun toolset(
        httpClient: OkHttpClient,
        credential: String = "key",
    ): KimiFormulaToolset {
        val service = mockk<LLMModelSetting> {
            every { id } returns SERVICE_ID
            every { isEnabled } returns true
            every { apiKey } returns credential
        }
        val modelServices = mockk<AgentModelConfigurationSource> {
            every { currentServices() } returns listOf(service)
        }
        return KimiFormulaToolset(
            cache = KimiFormulaCache(httpClient),
            httpClient = httpClient,
            modelServices = modelServices,
        )
    }

    private companion object {
        const val SERVICE_ID = "kimi"
        const val MANIFEST_BODY =
            """{"tools":[{"function":{"name":"translate","description":"Translate text"}}]}"""

        /** Short-circuits every manifest request with a canned response. */
        fun manifestClient(
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
