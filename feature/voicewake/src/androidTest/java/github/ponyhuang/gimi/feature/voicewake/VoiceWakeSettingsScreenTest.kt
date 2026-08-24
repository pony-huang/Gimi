package github.ponyhuang.gimi.feature.voicewake

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelState
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceWakeSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersBothModelsAndClickingEnglishRowEmitsSelectModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var action: VoiceWakeSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(voiceState = twoModelState()),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.voicewake_model_cn_name)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.voicewake_model_en_name)).performClick()

        assertEquals(
            VoiceWakeSettingsAction.SelectModel(WakeModelCatalog.English.id),
            action,
        )
    }

    @Test
    fun englishModelShowsFixedGimiWakeWordWithoutEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = twoModelState(activeModelId = WakeModelCatalog.English.id),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(
                R.string.voicewake_section_keyword_with_language,
                context.getString(R.string.voicewake_language_en),
            ),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.voicewake_keyword_en)).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun missingModelShowsDownloadButtonAndEmitsInstallAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installLabel = if (context.resources.configuration.locales[0].language == "zh") {
            "下载"
        } else {
            "Download"
        }
        var action: VoiceWakeSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(voiceState = twoModelState()),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onAllNodesWithText(installLabel).assertCountEquals(1)
        composeRule.onNodeWithText(installLabel).performClick()

        assertEquals(
            VoiceWakeSettingsAction.InstallModel(WakeModelCatalog.English.id),
            action,
        )
    }

    @Test
    fun runningListenerShowsLiveStatusInSubtitle() {
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = twoModelState().copy(
                            status = VoiceWakeStatus.Listening,
                            message = "正在监听“吉米”",
                            deviceName = "EDIFIER W820NB",
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("正在监听“吉米” · EDIFIER W820NB").assertExists()
        composeRule.onNodeWithText("监听中").assertDoesNotExist()
    }

    private fun twoModelState(
        activeModelId: String = WakeModelCatalog.Chinese.id,
    ) = VoiceWakeState(
        availableModels = WakeModelCatalog.models,
        activeModelId = activeModelId,
        modelStates = mapOf(
            WakeModelCatalog.Chinese.id to WakeModelState(WakeModelStatus.Ready, 1f),
            WakeModelCatalog.English.id to WakeModelState(WakeModelStatus.Missing),
        ),
    )
}
