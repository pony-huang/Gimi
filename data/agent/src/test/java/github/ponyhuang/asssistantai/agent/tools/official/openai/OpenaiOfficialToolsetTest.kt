package github.ponyhuang.asssistantai.agent.tools.official.openai

import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenaiOfficialToolsetTest {

    private val toolset = OpenaiOfficialToolset()

    @Test
    fun resolvesEnabledBuiltInToolsForAnyStandardService() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "custom-standard-service",
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), tools.map { it.name })
    }

    @Test
    fun ignoresToolsForAnthropicProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "openai",
                baseType = ApiProtocol.Anthropic,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresDisabledAndUnsupportedTools() = runTest {
        assertTrue(toolset.resolveTools(config("openai", officialTools = emptyList())).isEmpty())
        assertTrue(
            toolset.resolveTools(
                config("openai", officialTools = listOf(OfficialToolIds.KIMI_FORMULAS)),
            ).isEmpty(),
        )
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
