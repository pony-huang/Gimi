package github.ponyhuang.asssistantai.agent.tools.official.openai

import github.ponyhuang.asssistantai.agent.ModelRuntimeMetadata
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
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
            config(serviceId = "custom-standard-service"),
            selection = null,
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), tools.map { it.name })
    }

    @Test
    fun ignoresToolsForAnthropicProtocol() = runTest {
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
                "openai" to mapOf(OfficialToolIds.WEB_SEARCH to emptySet()),
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
