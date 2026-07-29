package github.ponyhuang.asssistantai.agent.tools.official.kimi

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

class KimiFormulaToolsetTest {

    @Test
    fun notApplicableWhenKimiFormulasNotEnabled() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(200, MANIFEST_BODY))
        val config = config(officialTools = emptyList())

        assertTrue(toolset.resolveTools(config).isEmpty())
    }

    @Test
    fun filteredByEnabledFunctionIds() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(200, MANIFEST_BODY))
        val config = config(
            enabledOfficialFunctions = mapOf(
                OfficialToolIds.KIMI_FORMULAS to setOf("translate"),
            ),
        )

        val tools = toolset.resolveTools(config)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun emptyWhenEnabledFunctionIdsMatchNothing() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(200, MANIFEST_BODY))
        val config = config(
            enabledOfficialFunctions = mapOf(
                OfficialToolIds.KIMI_FORMULAS to setOf("missing_function"),
            ),
        )

        assertTrue(toolset.resolveTools(config).isEmpty())
    }

    @Test
    fun allFunctionsWhenMarkerPresent() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(200, MANIFEST_BODY))
        val config = config(
            enabledOfficialFunctions = mapOf(
                OfficialToolIds.KIMI_FORMULAS to
                        setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            ),
        )

        val tools = toolset.resolveTools(config)

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun allFunctionsWhenNoConversationConfig() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(200, MANIFEST_BODY))

        val tools = toolset.resolveTools(config())

        assertEquals(listOf("translate"), tools.map { it.name })
    }

    @Test
    fun manifestEmptyWhenNetworkFails() = runTest {
        val toolset = KimiFormulaToolset(manifestClient(500, "{}"))

        assertTrue(toolset.resolveTools(config()).isEmpty())
    }

    private fun config(
        officialTools: List<String> = listOf(OfficialToolIds.KIMI_FORMULAS),
        enabledOfficialFunctions: Map<String, Set<String>> = emptyMap(),
    ) = ModelConfig(
        serviceId = "service",
        baseType = ApiProtocol.Standard,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = officialTools,
        enabledOfficialFunctions = enabledOfficialFunctions,
    )

    private companion object {
        const val MANIFEST_BODY =
            """{"tools":[{"function":{"name":"translate","description":"Translate text"}}]}"""

        /** Short-circuits every manifest request with a canned response. */
        fun manifestClient(code: Int, body: String): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
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
