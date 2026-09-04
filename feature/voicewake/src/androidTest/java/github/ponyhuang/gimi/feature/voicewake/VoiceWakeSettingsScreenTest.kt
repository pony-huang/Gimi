package github.ponyhuang.gimi.feature.voicewake

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
    fun englishModelShowsEditableWakeWordAndCounters() {
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
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        composeRule.onAllNodes(hasSetTextAction())[0].assertTextContains("Gimi")
        composeRule.onNodeWithText("1/4 · 4/40").assertExists()
    }

    @Test
    fun editorEmitsDraftSuggestionClearAndSaveActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actions = mutableListOf<VoiceWakeSettingsAction>()
        val state = VoiceWakeSettingsUiState(
            voiceState = twoModelState(),
            keywordDraft = "小助手",
            hasUnsavedKeyword = true,
        )
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(state = state, onAction = actions::add)
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("语音助手")
        composeRule.onNodeWithText("吉米").performClick()
        composeRule.onNodeWithText(context.getString(R.string.voicewake_keyword_save))
            .assertIsEnabled()
            .performClick()

        assertEquals(VoiceWakeSettingsAction.KeywordChanged("语音助手"), actions[0])
        assertEquals(VoiceWakeSettingsAction.SuggestedKeywordSelected("吉米"), actions[1])
        assertEquals(VoiceWakeSettingsAction.SaveKeyword, actions[2])
    }

    @Test
    fun legacyBluetoothOnlySettingIsAbsent() {
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(voiceState = twoModelState()),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("仅蓝牙耳机时触发").assertDoesNotExist()
        composeRule.onNodeWithText("Trigger only with Bluetooth headset").assertDoesNotExist()
    }

    @Test
    fun optionalOverlayPermissionOpensSystemSettings() {
        var opened = false
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(voiceState = twoModelState()),
                    overlayPermissionGranted = false,
                    onOpenOverlaySettings = { opened = true },
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("允许悬浮显示").performClick()

        assertEquals(true, opened)
        composeRule.onNodeWithText("未授权，后台仍通过通知和语音反馈").assertExists()
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
    fun downloadingModelShowsIndeterminateBusyIndicator() {
        composeRule.setContent {
            AsssistantaiTheme {
                VoiceWakeSettingsScreen(
                    state = VoiceWakeSettingsUiState(
                        voiceState = twoModelState().copy(
                            modelStates = twoModelState().modelStates + (
                                WakeModelCatalog.English.id to WakeModelState(
                                    WakeModelStatus.Downloading,
                                    0f,
                                )
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodes(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
        ).assertCountEquals(1)
    }

    @Test
    fun readyModelShowsRemoveButtonAndEmitsRemoveAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val removeLabel = if (context.resources.configuration.locales[0].language == "zh") {
            "移除"
        } else {
            "Remove"
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

        composeRule.onNodeWithText(removeLabel).performClick()

        assertEquals("RemoveModel", action?.javaClass?.simpleName)
        assertEquals(
            WakeModelCatalog.Chinese.id,
            action?.javaClass?.getMethod("getModelId")?.invoke(action),
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
