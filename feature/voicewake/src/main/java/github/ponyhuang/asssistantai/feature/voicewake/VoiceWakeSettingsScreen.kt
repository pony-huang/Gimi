package github.ponyhuang.asssistantai.feature.voicewake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordError
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelInfo
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelSource
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelState
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
import github.ponyhuang.asssistantai.feature.voicewake.R
import github.ponyhuang.asssistantai.ui.settings.SettingsBanner
import github.ponyhuang.asssistantai.ui.settings.SettingsBannerTone
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun VoiceWakeSettingsScreen(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsListItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = stringResource(R.string.voicewake_listening_title),
                    subtitle = listeningSubtitle(state.voiceState),
                    onClick = {
                        onAction(VoiceWakeSettingsAction.ToggleListening(!state.voiceState.isRunning))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.voiceState.isRunning,
                            onCheckedChange = {
                                onAction(VoiceWakeSettingsAction.ToggleListening(it))
                            },
                        )
                    },
                )
            }
            if (!state.configurationReady) {
                item {
                    SettingsBanner(
                        text = stringResource(R.string.voicewake_offline_setup_required),
                        tone = SettingsBannerTone.Error,
                    )
                }
            }

            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.voicewake_section_models),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(state.voiceState.availableModels, key = { it.id }) { model ->
                WakeModelRow(
                    model = model,
                    modelState = state.voiceState.modelStates[model.id] ?: WakeModelState(),
                    isActive = state.voiceState.activeModelId == model.id,
                    onSelect = { onAction(VoiceWakeSettingsAction.SelectModel(model.id)) },
                    onInstall = { onAction(VoiceWakeSettingsAction.InstallModel(model.id)) },
                )
            }

            item {
                SettingsSectionTitle(
                    text = stringResource(
                        R.string.voicewake_section_keyword_with_language,
                        stringResource(languageNameRes(state.voiceState.activeModel)),
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item { KeywordEditor(state = state, onAction = onAction) }
        }
    }
}

/** 运行中展示服务层实时状态（含唤醒词与设备名），停止时展示功能说明。 */
@Composable
private fun listeningSubtitle(voiceState: VoiceWakeState): String {
    val liveStatus = voiceState.message?.takeIf { voiceState.isRunning }
    if (liveStatus != null) {
        return listOfNotNull(liveStatus, voiceState.deviceName).joinToString(" · ")
    }
    return stringResource(R.string.voicewake_listening_subtitle)
}

@Composable
private fun WakeModelRow(
    model: WakeModelInfo,
    modelState: WakeModelState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(modelNameRes(model.id)),
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            when (modelState.status) {
                WakeModelStatus.Missing -> when (val source = model.source) {
                    is WakeModelSource.Bundled ->
                        Text(stringResource(R.string.voicewake_model_status_bundled))
                    is WakeModelSource.Downloadable ->
                        Text(
                            stringResource(
                                R.string.voicewake_model_status_download_required,
                                source.sizeBytes / 1_000_000,
                            ),
                        )
                }
                WakeModelStatus.Downloading -> Column {
                    val progressPercent = (modelState.progress * 100).toInt()
                    Text(
                        stringResource(
                            R.string.voicewake_model_downloading_progress,
                            progressPercent,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { modelState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                WakeModelStatus.Extracting -> Text(stringResource(R.string.voicewake_model_installing))
                WakeModelStatus.Ready -> Text(stringResource(R.string.voicewake_model_ready))
                WakeModelStatus.Error -> Text(
                    modelState.message ?: stringResource(R.string.voicewake_model_install_failed),
                )
            }
        },
        leadingContent = {
            RadioButton(selected = isActive, onClick = null)
        },
        trailingContent = {
            if (modelState.status == WakeModelStatus.Missing ||
                modelState.status == WakeModelStatus.Error
            ) {
                TextButton(onClick = onInstall) {
                    Text(
                        stringResource(
                            if (modelState.status == WakeModelStatus.Error) R.string.voicewake_action_retry
                            else R.string.voicewake_action_install,
                        ),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isActive, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun KeywordEditor(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
) {
    val isEnglish = state.voiceState.activeModel.languageTag.startsWith("en")
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = state.keywordDraft,
            onValueChange = {
                onAction(VoiceWakeSettingsAction.KeywordChanged(it))
            },
            // 组标题已是「唤醒词」，不再重复 label；规则说明交给 supportingText。
            supportingText = {
                val errorRes = keywordErrorRes(state.keywordError, isEnglish)
                Text(
                    if (errorRes != null) {
                        stringResource(errorRes)
                    } else {
                        stringResource(
                            if (isEnglish) R.string.voicewake_keyword_hint_en
                            else R.string.voicewake_keyword_hint_zh,
                        )
                    },
                )
            },
            isError = state.keywordError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(VoiceWakeSettingsAction.SaveKeyword) },
            enabled = state.keywordDraft.trim() != state.voiceState.keyword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.voicewake_save_keyword))
        }
    }
}

private fun keywordErrorRes(error: WakeKeywordError?, isEnglish: Boolean): Int? = when (error) {
    WakeKeywordError.InvalidLength ->
        if (isEnglish) R.string.voicewake_keyword_error_length_en
        else R.string.voicewake_keyword_error_length_zh
    WakeKeywordError.InvalidCharacters -> R.string.voicewake_keyword_error_characters
    WakeKeywordError.InvalidWordFormat -> R.string.voicewake_keyword_error_format_en
    null -> null
}

private fun modelNameRes(modelId: String): Int = when (modelId) {
    WakeModelCatalog.English.id -> R.string.voicewake_model_en_name
    else -> R.string.voicewake_model_cn_name
}

private fun languageNameRes(model: WakeModelInfo): Int =
    if (model.languageTag.startsWith("en")) R.string.voicewake_language_en
    else R.string.voicewake_language_zh
