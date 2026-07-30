package github.ponyhuang.gimi.feature.voicewake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordError
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
        var action: VoiceWakeSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(voiceState = twoModelState()),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("中文唤醒模型").assertExists()
        composeRule.onNodeWithText("英语唤醒模型").performClick()

        assertEquals(
            VoiceWakeSettingsAction.SelectModel(WakeModelCatalog.English.id),
            action,
        )
    }

    @Test
    fun englishModelErrorKeywordShowsEnglishFormatMessage() {
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = twoModelState(activeModelId = WakeModelCatalog.English.id),
                        keywordDraft = "Hey",
                        keywordError = WakeKeywordError.InvalidWordFormat,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("唤醒词 · English").assertExists()
        composeRule.onNodeWithText("请输入 2–4 个小写英文单词，用空格分隔").assertExists()
    }

    @Test
    fun runningListenerShowsLiveStatusInSubtitle() {
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = twoModelState().copy(
                            status = VoiceWakeStatus.Listening,
                            message = "正在监听“你好助手”",
                            deviceName = "EDIFIER W820NB",
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("正在监听“你好助手” · EDIFIER W820NB").assertExists()
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
