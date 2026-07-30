package github.ponyhuang.gimi.agent

import github.ponyhuang.gimi.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolSelectionTest {

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
    fun officialToolIsEnabledWithAnyFunctionSelected() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to setOf("web_search")),
            ),
        )

        assertTrue(configuration.isOfficialToolEnabled("mimo", "web_search"))
    }

    @Test
    fun officialToolIsDisabledWhenNoFunctionsAreSelected() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to emptySet()),
            ),
        )

        assertFalse(configuration.isOfficialToolEnabled("mimo", "web_search"))
    }

    @Test
    fun officialToolIsDisabledWhenAbsentFromConversationSelection() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to setOf("web_search")),
            ),
        )

        assertFalse(configuration.isOfficialToolEnabled("mimo", "kimi_formulas"))
    }

    @Test
    fun absentConversationSelectionEnablesEveryServiceLevelTool() {
        val configuration: ConversationToolConfiguration? = null

        assertTrue(configuration.isOfficialToolEnabled("mimo", "web_search"))
        assertTrue(configuration.isOfficialToolEnabled("mimo", "kimi_formulas"))
    }
}
