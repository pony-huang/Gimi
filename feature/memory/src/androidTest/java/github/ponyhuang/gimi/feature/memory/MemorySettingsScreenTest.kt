package github.ponyhuang.gimi.feature.memory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                )
            }
        }

        composeRule.onNodeWithText("Mem0").assertIsDisplayed()
        composeRule.onNodeWithText("Mem0 API Token 不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("启用 Mem0").performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(
            listOf(
                MemorySettingsAction.SetMem0Enabled(true),
                MemorySettingsAction.Save,
            ),
            actions,
        )
    }

}
