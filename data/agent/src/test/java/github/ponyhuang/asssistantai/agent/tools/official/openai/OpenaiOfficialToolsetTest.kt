package github.ponyhuang.asssistantai.agent.tools.official.openai

import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenaiOfficialToolsetTest {

    private val toolset = OpenaiOfficialToolset()

    @Test
    fun applicableWhenOpenaiStandard() {
        val config = config(serviceId = "openai", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.isApplicable(config))
        assertEquals(1, toolset.openAiNativeSpecs(config).size)
    }

    @Test
    fun applicableWhenMimoStandard() {
        val config = config(serviceId = "mimo", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.isApplicable(config))
        assertEquals(1, toolset.openAiNativeSpecs(config).size)
    }

    @Test
    fun notApplicableWhenMismatchedProtocol() {
        val config = config(
            serviceId = "openai",
            baseType = ApiProtocol.Anthropic,
            officialTools = listOf(OfficialToolIds.WEB_SEARCH),
        )

        assertFalse(toolset.isApplicable(config))
        assertTrue(toolset.openAiNativeSpecs(config).isEmpty())
    }

    @Test
    fun notApplicableWhenToolNotInOfficialTools() {
        val config = config(serviceId = "openai", officialTools = emptyList())

        assertTrue(toolset.openAiNativeSpecs(config).isEmpty())
    }

    @Test
    fun webSearchOpenAiSpecHasCorrectNameAndType() {
        val config = config(serviceId = "openai", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        val spec = toolset.openAiNativeSpecs(config).single() as NativeToolSpec.OpenAi

        assertEquals(OfficialToolIds.WEB_SEARCH, spec.toolId)
        assertTrue(spec.tool.isFunction())
        assertEquals(OfficialToolIds.WEB_SEARCH, spec.tool.asFunction().function().name())
    }

    @Test
    fun emptyOpenAiSpecsWhenNoNativeIdsEnabled() {
        val config = config(serviceId = "mimo", officialTools = listOf(OfficialToolIds.KIMI_FORMULAS))

        assertTrue(toolset.openAiNativeSpecs(config).isEmpty())
    }

    @Test
    fun anthropicNativeSpecsAlwaysEmpty() {
        val config = config(serviceId = "openai", officialTools = listOf(OfficialToolIds.WEB_SEARCH))

        assertTrue(toolset.anthropicNativeSpecs(config).isEmpty())
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Standard,
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
