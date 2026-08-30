package github.ponyhuang.gimi.data.agent.tools.official.anthropic

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicOfficialToolsetTest {

    private val toolset = AnthropicOfficialToolset()

    @Test
    fun resolvesEnabledBuiltInToolsForAnthropicService() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "anthropic"),
            selection = null,
        )

        assertEquals(listOf(AnthropicOfficialToolset.WEB_SEARCH_TOOL_ID), tools.map { it.name })
    }

    @Test
    fun ignoresOtherAnthropicServices() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "minimax"),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresAnthropicServiceUsingStandardProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "anthropic",
                baseType = ApiProtocol.Standard,
            ),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun dropsToolWhenConversationSelectionExcludesIt() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "anthropic" to mapOf(AnthropicOfficialToolset.WEB_SEARCH_TOOL_ID to emptySet()),
            ),
        )

        val tools = toolset.resolveTools(config("anthropic"), selection)

        assertTrue(tools.isEmpty())
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Anthropic,
    ) = ModelRuntimeMetadata(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        fullBaseUrl = "https://example.com",
    )
}
