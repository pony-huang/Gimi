package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DefaultModelSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingAssistantOptionRaisesDialogAction() {
        var action: DefaultModelSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                DefaultModelSettingsScreen(
                    state = DefaultModelSettingsUiState(),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("默认模型").performClick()

        assertEquals(
            DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Assistant),
            action,
        )
    }
}
