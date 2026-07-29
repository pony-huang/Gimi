package github.ponyhuang.asssistantai.agent

import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialToolIntegrationTest {

    @Test
    fun registryAggregatesToolsAndNativeSpecsFromApplicableToolsets() = runBlocking {
        val registry = OfficialToolRegistry(
            toolsets = setOf(
                OpenaiOfficialToolset(),
                AnthropicOfficialToolset(),
                KimiFormulaToolset(OkHttpClient()),
            ),
        )

        // Mimo + Standard + web_search → OpenAI toolset contributes native spec
        val mimo = registry.resolve(
            config(
                serviceId = "mimo",
                baseType = ApiProtocol.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )
        assertEquals(1, mimo.openAiNativeSpecs.size)
        assertEquals(OfficialToolIds.WEB_SEARCH, mimo.openAiNativeSpecs.single().toolId)
        assertTrue(mimo.anthropicNativeSpecs.isEmpty())
        assertTrue(mimo.tools.isEmpty())

        // Anthropic + web_search → Anthropic toolset contributes native spec
        val anth = registry.resolve(
            config(
                serviceId = "anthropic",
                baseType = ApiProtocol.Anthropic,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )
        assertEquals(1, anth.anthropicNativeSpecs.size)
        assertEquals(OfficialToolIds.WEB_SEARCH, anth.anthropicNativeSpecs.single().toolId)
        assertTrue(anth.openAiNativeSpecs.isEmpty())

        // Cross-protocol mismatch: openai service + Anthropic protocol → all empty
        val cross = registry.resolve(
            config(
                serviceId = "openai",
                baseType = ApiProtocol.Anthropic,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )
        assertTrue(cross.openAiNativeSpecs.isEmpty())
        assertTrue(cross.anthropicNativeSpecs.isEmpty())
        assertTrue(cross.tools.isEmpty())
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol,
        officialTools: List<String>,
    ) = ModelConfig(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = officialTools,
    )
}
