package github.ponyhuang.asssistantai.agent.tools.official.anthropic

import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicOfficialToolsetTest {

    private val toolset = AnthropicOfficialToolset()

    @Test
    fun applicableWhenAnthropicService() {
        val config = config(serviceId = "anthropic", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.isApplicable(config))
        assertEquals(1, toolset.anthropicNativeSpecs(config).size)
    }

    @Test
    fun applicableWhenMinimaxService() {
        val config = config(serviceId = "minimax", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.isApplicable(config))
        assertEquals(1, toolset.anthropicNativeSpecs(config).size)
    }

    @Test
    fun notApplicableWhenMismatchedProtocol() {
        val config = config(
            serviceId = "anthropic",
            baseType = ApiProtocol.Standard,
            officialTools = listOf(OfficialToolIds.WEB_SEARCH),
        )

        assertFalse(toolset.isApplicable(config))
        assertTrue(toolset.anthropicNativeSpecs(config).isEmpty())
    }

    @Test
    fun notApplicableWhenToolNotInOfficialTools() {
        val config = config(serviceId = "anthropic", officialTools = emptyList())

        assertTrue(toolset.anthropicNativeSpecs(config).isEmpty())
    }

    @Test
    fun webSearchAnthropicSpecIsWebSearchTool20250305() {
        val config = config(serviceId = "anthropic", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        val spec = toolset.anthropicNativeSpecs(config).single() as NativeToolSpec.Anthropic

        assertEquals(OfficialToolIds.WEB_SEARCH, spec.toolId)
        assertTrue(spec.tool.isWebSearchTool20250305())
    }

    @Test
    fun openAiNativeSpecsAlwaysEmpty() {
        val config = config(serviceId = "anthropic", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.openAiNativeSpecs(config).isEmpty())
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Anthropic,
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
