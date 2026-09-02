package github.ponyhuang.gimi.feature.mcp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class McpServerListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingServerRaisesNavigationCallback() {
        var editedId: String? = null
        composeRule.setContent {
            AsssistantaiTheme {
                McpServerListScreen(
                    state = McpSettingsUiState(
                        servers = listOf(McpServer(id = "server", name = "Server")),
                    ),
                    onAction = {},
                    onNavigateToEditor = { editedId = it },
                    onCreateServer = {},
                    onImportServers = {},
                )
            }
        }

        composeRule.onNodeWithText("Server").performClick()

        assertEquals("server", editedId)
    }

    @Test
    fun loadedServerPrefersMcpMetadataAndDisplaysToolDescription() {
        composeRule.setContent {
            AsssistantaiTheme {
                McpServerListScreen(
                    state = McpSettingsUiState(
                        servers = listOf(
                            McpServer(
                                id = "server",
                                name = "本地名称",
                                description = "本地说明",
                            ),
                        ),
                        expandedServerId = "server",
                        capabilities = mapOf(
                            "server" to ServerCapabilityState.Loaded(
                                result = McpProbeResult(
                                    reachable = true,
                                    serverName = "服务端名称",
                                    serverVersion = "服务端说明",
                                    tools = listOf(
                                        McpToolSummary(
                                            name = "网页搜索",
                                            description = "搜索公开网页内容",
                                        ),
                                    ),
                                ),
                                serverSnapshot = McpServer(id = "server"),
                            ),
                        ),
                    ),
                    onAction = {},
                    onNavigateToEditor = {},
                    onCreateServer = {},
                    onImportServers = {},
                )
            }
        }

        composeRule.onNodeWithText("服务端名称").assertIsDisplayed()
        composeRule.onNodeWithText("服务端说明").assertIsDisplayed()
        composeRule.onNodeWithText("网页搜索").assertIsDisplayed()
        composeRule.onNodeWithText("搜索公开网页内容").assertIsDisplayed()
    }
}
