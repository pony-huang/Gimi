package github.ponyhuang.asssistantai.feature.voicewake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
import github.ponyhuang.asssistantai.feature.voicewake.R
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
            item { SettingsSectionTitle(text = stringResource(R.string.voicewake_section_listening)) }
            item {
                SettingsListItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = stringResource(R.string.voicewake_listening_title),
                    subtitle = stringResource(R.string.voicewake_listening_subtitle),
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

            item { SettingsSectionTitle(text = stringResource(R.string.voicewake_section_keyword)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = state.keywordDraft,
                        onValueChange = {
                            onAction(VoiceWakeSettingsAction.KeywordChanged(it))
                        },
                        // 组标题已是「唤醒词」，不再重复 label；规则说明交给 supportingText。
                        supportingText = {
                            val errorText = state.keywordError
                            Text(
                                if (errorText != null) {
                                    stringResource(R.string.voicewake_keyword_error)
                                } else {
                                    stringResource(R.string.voicewake_keyword_hint)
                                },
                            )
                        },
                        isError = state.keywordError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onAction(VoiceWakeSettingsAction.SaveKeyword) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.voicewake_save_keyword))
                    }
                }
            }

            item {
                SettingsSectionTitle(
                    text = stringResource(R.string.voicewake_offline_model),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item {
                WakeModelRow(
                    voiceState = state.voiceState,
                    onInstall = { onAction(VoiceWakeSettingsAction.InstallModel) },
                )
            }
            if (!state.configurationReady) {
                item {
                    Text(
                        text = stringResource(R.string.voicewake_offline_setup_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WakeModelRow(
    voiceState: VoiceWakeState,
    onInstall: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(R.string.voicewake_offline_model_name),
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            when (voiceState.model.status) {
                WakeModelStatus.Missing -> Text(stringResource(R.string.voicewake_model_status_bundled))
                WakeModelStatus.Downloading -> Column {
                    val progressPercent = (voiceState.model.progress * 100).toInt()
                    Text(
                        stringResource(
                            R.string.voicewake_model_extracting_progress,
                            progressPercent,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { voiceState.model.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WakeModelStatus.Extracting -> Text(stringResource(R.string.voicewake_model_installing))
                WakeModelStatus.Ready -> Text(stringResource(R.string.voicewake_model_ready))
                WakeModelStatus.Error -> Text(
                    voiceState.model.message
                        ?: stringResource(R.string.voicewake_model_install_failed),
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            if (voiceState.model.status == WakeModelStatus.Missing ||
                voiceState.model.status == WakeModelStatus.Error
            ) {
                TextButton(onClick = onInstall) {
                    Text(
                        stringResource(
                            if (voiceState.model.status == WakeModelStatus.Error) R.string.voicewake_action_retry
                            else R.string.voicewake_action_install,
                        ),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
