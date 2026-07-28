package github.ponyhuang.asssistantai.agent

import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelType
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationToolSelectionTest {

    @Test
    fun conversationOfficialToolsAreLimitedToToolsSupportedByTheCurrentModel() {
        val model = ModelConfig(
            serviceId = LLMModelType.Mimo.serviceId,
            baseType = ApiBaseType.Standard,
            modelId = "model",
            apiKey = "key",
            fullBaseUrl = "https://example.com",
            supportedOfficialTools = listOf("web_search"),
            officialTools = listOf("web_search"),
        )
        val configuration = ConversationToolConfiguration(
            enabledOfficialToolIdsByService = mapOf(
                LLMModelType.Mimo.serviceId to setOf("web_search", "unsupported_tool"),
            ),
        )

        assertEquals(
            listOf("web_search"),
            model.forConversation(configuration).officialTools,
        )
    }

    @Test
    fun sessionMcpSelectionIgnoresGlobalEnabledFlagAfterSnapshot() {
        val globallyDisabled = McpServer(id = "selected", isEnabled = false)
        val globallyEnabled = McpServer(id = "not-selected", isEnabled = true)

        val selected = selectMcpServers(
            servers = listOf(globallyDisabled, globallyEnabled),
            selectedServerIds = setOf("selected"),
        )

        assertEquals(listOf("selected"), selected.map(McpServer::id))
    }

    @Test
    fun absentSessionSelectionUsesGlobalMcpDefaultsForNonChatEntryPoints() {
        val disabled = McpServer(id = "disabled", isEnabled = false)
        val enabled = McpServer(id = "enabled", isEnabled = true)

        val selected = selectMcpServers(
            servers = listOf(disabled, enabled),
            selectedServerIds = null,
        )

        assertEquals(listOf("enabled"), selected.map(McpServer::id))
    }
}
