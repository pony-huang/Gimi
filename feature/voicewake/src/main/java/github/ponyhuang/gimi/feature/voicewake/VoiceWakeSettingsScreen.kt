package github.ponyhuang.gimi.feature.voicewake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.WakeModelSource
import github.ponyhuang.gimi.domain.speech.model.WakeModelState
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.feature.voicewake.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun VoiceWakeSettingsScreen(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceListItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = stringResource(R.string.voicewake_listening_title),
                    subtitle = listeningSubtitle(state.voiceState),
                    onClick = {
                        onAction(
                            VoiceWakeSettingsAction.ToggleListening(
                                !state.voiceState.isRunning && !state.isStartPending,
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.voiceState.isRunning || state.isStartPending,
                            onCheckedChange = {
                                onAction(VoiceWakeSettingsAction.ToggleListening(it))
                            },
                        )
                    },
                )
            }
            item {
                PreferenceListItem(
                    icon = Icons.Default.Bluetooth,
                    title = stringResource(R.string.voicewake_bluetooth_only_title),
                    subtitle = stringResource(R.string.voicewake_bluetooth_only_subtitle),
                    onClick = {
                        onAction(
                            VoiceWakeSettingsAction.SetBluetoothOnly(
                                !state.voiceState.bluetoothOnly,
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.voiceState.bluetoothOnly,
                            onCheckedChange = {
                                onAction(VoiceWakeSettingsAction.SetBluetoothOnly(it))
                            },
                        )
                    },
                )
            }
            if (!state.configurationReady) {
                item {
                    PreferenceBanner(
                        text = stringResource(R.string.voicewake_offline_setup_required),
                        tone = PreferenceBannerTone.Error,
                    )
                }
            }

            item {
                PreferenceSectionTitle(
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
                    onCancel = { onAction(VoiceWakeSettingsAction.CancelInstall(model.id)) },
                    onRemove = { onAction(VoiceWakeSettingsAction.RemoveModel(model.id)) },
                )
            }

            item {
                PreferenceSectionTitle(
                    text = stringResource(
                        R.string.voicewake_section_keyword_with_language,
                        stringResource(languageNameRes(state.voiceState.activeModel)),
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item {
                Column {
                    Text(
                        text = stringResource(wakeWordRes(state.voiceState.activeModelId)),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        text = stringResource(R.string.voicewake_keyword_fixed_description),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
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
    onCancel: () -> Unit,
    onRemove: () -> Unit,
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
                WakeModelStatus.Removing -> Text(stringResource(R.string.voicewake_model_removing))
                WakeModelStatus.Ready -> Text(
                    stringResource(
                        R.string.voicewake_model_ready,
                        stringResource(wakeWordRes(model.id)),
                    ),
                )
                WakeModelStatus.Error -> Text(
                    modelState.message ?: stringResource(R.string.voicewake_model_install_failed),
                )
            }
        },
        leadingContent = {
            RadioButton(selected = isActive, onClick = null)
        },
        trailingContent = {
            if (modelState.status == WakeModelStatus.Missing) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.voicewake_action_download))
                }
            } else if (modelState.status == WakeModelStatus.Error) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.voicewake_action_retry))
                }
            } else if (modelState.status == WakeModelStatus.Downloading ||
                modelState.status == WakeModelStatus.Extracting
            ) {
                TextButton(onClick = onCancel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.voicewake_action_cancel))
                }
            } else if (modelState.status == WakeModelStatus.Ready) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.voicewake_action_remove),
                        color = MaterialTheme.colorScheme.error,
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

private fun modelNameRes(modelId: String): Int = when (modelId) {
    WakeModelCatalog.English.id -> R.string.voicewake_model_en_name
    else -> R.string.voicewake_model_cn_name
}

private fun languageNameRes(model: WakeModelInfo): Int =
    if (model.languageTag.startsWith("en")) R.string.voicewake_language_en
    else R.string.voicewake_language_zh

private fun wakeWordRes(modelId: String): Int = when (modelId) {
    WakeModelCatalog.English.id -> R.string.voicewake_keyword_en
    else -> R.string.voicewake_keyword_zh
}
