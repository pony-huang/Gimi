package github.ponyhuang.gimi.feature.mcp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.mcp.model.McpServer
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
}
