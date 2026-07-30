package github.ponyhuang.asssistantai.agent.tools.official.anthropic

import github.ponyhuang.asssistantai.agent.ModelRuntimeMetadata
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
            config(serviceId = "custom-anthropic-service"),
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
            ),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun dropsToolWhenConversationSelectionExcludesIt() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "anthropic" to mapOf(OfficialToolIds.WEB_SEARCH to emptySet()),
            ),
        )

        val tools = toolset.resolveTools(
            config("anthropic"),
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
