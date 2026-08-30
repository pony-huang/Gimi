package github.ponyhuang.gimi.data.agent.tools.official.openai

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenaiOfficialToolsetTest {

    private val toolset = OpenaiOfficialToolset()

    @Test
    fun resolvesEnabledBuiltInToolsForOpenaiService() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "openai"),
            selection = null,
        )

        assertEquals(listOf(OpenaiOfficialToolset.WEB_SEARCH_TOOL_ID), tools.map { it.name })
    }

    @Test
    fun ignoresOtherStandardServices() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "mimo"),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresOpenaiServiceUsingAnthropicProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "openai",
                baseType = ApiProtocol.Anthropic,
            ),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun dropsToolWhenConversationSelectionExcludesIt() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "openai" to mapOf(OpenaiOfficialToolset.WEB_SEARCH_TOOL_ID to emptySet()),
            ),
        )

        val tools = toolset.resolveTools(config("openai"), selection)

        assertTrue(tools.isEmpty())
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Standard,
    ) = ModelRuntimeMetadata(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        fullBaseUrl = "https://example.com",
    )
}
