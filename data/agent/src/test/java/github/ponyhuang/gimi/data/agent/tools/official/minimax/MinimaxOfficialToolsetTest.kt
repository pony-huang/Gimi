package github.ponyhuang.gimi.data.agent.tools.official.minimax

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimaxOfficialToolsetTest {

    private val toolset = MinimaxOfficialToolset()

    @Test
    fun resolvesEnabledBuiltInToolsForMiniMaxAnthropicService() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "minimax"),
            selection = null,
        )

        assertEquals(listOf(MinimaxOfficialToolset.WEB_SEARCH_TOOL_ID), tools.map { it.name })
    }

    @Test
    fun ignoresOtherAnthropicServices() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "anthropic"),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresMiniMaxStandardProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "minimax",
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
                "minimax" to mapOf(MinimaxOfficialToolset.WEB_SEARCH_TOOL_ID to emptySet()),
            ),
        )

        val tools = toolset.resolveTools(
            config("minimax"),
            selection,
        )

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
