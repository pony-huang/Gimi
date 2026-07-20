package github.ponyhuang.asssistantai.feature.voicewake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceWakeSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingSaveRaisesSynchronousAction() {
        var action: VoiceWakeSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("保存唤醒词").performClick()

        assertEquals(VoiceWakeSettingsAction.SaveKeyword, action)
    }
}
