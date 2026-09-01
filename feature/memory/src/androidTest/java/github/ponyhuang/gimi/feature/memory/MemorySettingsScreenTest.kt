package github.ponyhuang.gimi.feature.memory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MemorySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enableAndSaveActionsLeaveTheStatelessScreen() {
        val actions = mutableListOf<MemorySettingsAction>()
        composeRule.setContent {
            AsssistantaiTheme {
                MemorySettingsScreen(
                    state = MemorySettingsUiState(tokenError = true),
                    onAction = actions::add,
                    onNavigateToHistory = {},
                    onOpenMem0Quickstart = {},
                )
            }
        }

        // Mem0 关闭时 Token 配置区整体隐藏
        composeRule.onNodeWithText("启用 Mem0").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertDoesNotExist()

        composeRule.onNodeWithText("启用 Mem0").performClick()
        composeRule.onNodeWithText("Mem0 API Token 不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed().performClick()

        assertEquals(
            listOf(
                MemorySettingsAction.SetMem0Enabled(true),
                MemorySettingsAction.Save,
            ),
            actions,
        )
    }

    @Test
    fun saveButtonUsesTheCenteredHalfWidthPrimaryActionLayout() {
        composeRule.setContent {
            AsssistantaiTheme {
                MemorySettingsScreen(
                    state = MemorySettingsUiState(mem0Enabled = true),
                    onAction = {},
                    onNavigateToHistory = {},
                    onOpenMem0Quickstart = {},
                )
            }
        }

        assertCenteredHalfWidth("memory_save_action")
    }

    private fun assertCenteredHalfWidth(tag: String) {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

        assertEquals(root.width / 2f, button.width, 24f)
        assertEquals(root.left + root.width / 2f, button.left + button.width / 2f, 1f)
    }

}
