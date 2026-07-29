package github.ponyhuang.asssistantai.agent

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolSelectionTest {

    @Test
    fun conversationOfficialToolsAreLimitedToToolsSupportedByTheCurrentModel() {
        val model = ModelConfig(
            serviceId = "mimo",
            baseType = ApiProtocol.Standard,
            modelId = "model",
            apiKey = "key",
            fullBaseUrl = "https://example.com",
            officialTools = listOf("web_search"),
        )
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to setOf("web_search")),
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

    @Test
    fun forConversationKeepsOfficialToolWithAnyFunctionSelected() {
        val model = ModelConfig(
            serviceId = "mimo",
            baseType = ApiProtocol.Standard,
            modelId = "model",
            apiKey = "key",
            fullBaseUrl = "https://example.com",
            officialTools = listOf("web_search"),
        )
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to setOf("web_search")),
            ),
        )

        val resolved = model.forConversation(configuration)

        assertEquals(listOf("web_search"), resolved.officialTools)
        assertTrue(resolved.enabledOfficialFunctions.containsKey("web_search"))
    }

    @Test
    fun forConversationDropsOfficialToolWhenNoFunctionsAreSelected() {
        val model = ModelConfig(
            serviceId = "mimo",
            baseType = ApiProtocol.Standard,
            modelId = "model",
            apiKey = "key",
            fullBaseUrl = "https://example.com",
            officialTools = listOf("web_search"),
        )
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to emptySet()),
            ),
        )

        assertEquals(emptyList<String>(), model.forConversation(configuration).officialTools)
    }
}
