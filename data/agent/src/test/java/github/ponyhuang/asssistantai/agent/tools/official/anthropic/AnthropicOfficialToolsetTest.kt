package github.ponyhuang.asssistantai.agent.tools.official.anthropic

import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicOfficialToolsetTest {

    private val toolset = AnthropicOfficialToolset()

    @Test
    fun resolvesEnabledBuiltInToolsForAnyAnthropicService() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "custom-anthropic-service",
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
            selection = null,
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), tools.map { it.name })
    }

    @Test
    fun ignoresToolsForStandardProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "anthropic",
                baseType = ApiProtocol.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresDisabledAndUnsupportedTools() = runTest {
        assertTrue(
            toolset.resolveTools(config("anthropic", officialTools = emptyList()), null).isEmpty(),
        )
        assertTrue(
            toolset.resolveTools(
                config("anthropic", officialTools = listOf(OfficialToolIds.KIMI_FORMULAS)),
                selection = null,
            ).isEmpty(),
        )
    }

    @Test
    fun dropsToolWhenConversationSelectionExcludesIt() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "anthropic" to mapOf(OfficialToolIds.WEB_SEARCH to emptySet()),
            ),
        )

        val tools = toolset.resolveTools(
            config("anthropic", officialTools = listOf(OfficialToolIds.WEB_SEARCH)),
            selection,
        )

        assertTrue(tools.isEmpty())
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
