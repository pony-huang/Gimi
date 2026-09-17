package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.feature.assistant.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Rule
import org.junit.Test

class VoiceWakeSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableRecognizerShowsExplanation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = VoiceWakeState(recognizerAvailable = false),
                        configurationReady = true,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.voicewake_recognizer_unavailable),
        ).assertExists()
    }
}
