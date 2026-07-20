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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
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
            item { SettingsSectionTitle(text = "监听") }
            item {
                SettingsListItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = "后台监听",
                    subtitle = "连接蓝牙耳机后，可通过唤醒词在后台执行任务",
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

            item { SettingsSectionTitle(text = "唤醒词") }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = state.keywordDraft,
                        onValueChange = {
                            onAction(VoiceWakeSettingsAction.KeywordChanged(it))
                        },
                        label = { Text("唤醒词") },
                        supportingText = {
                            Text(state.keywordError ?: "2–20 个字符，例如：你好助手")
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
                        Text("保存唤醒词")
                    }
                }
            }

            item {
                SettingsSectionTitle(
                    text = "离线模型",
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
                        text = "启用前请配置可用的默认助手模型和语音识别模型。",
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
        headlineContent = { Text("离线中文唤醒模型", fontWeight = FontWeight.Medium) },
        supportingContent = {
            when (voiceState.model.status) {
                WakeModelStatus.Missing -> Text("已内置 APK，首次启用时安装")
                WakeModelStatus.Downloading -> Column {
                    Text("正在读取内置模型 ${(voiceState.model.progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { voiceState.model.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WakeModelStatus.Extracting -> Text("正在安装模型")
                WakeModelStatus.Ready -> Text("已安装，仅在本机识别唤醒词")
                WakeModelStatus.Error -> Text(voiceState.model.message ?: "安装失败")
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
                    Text(if (voiceState.model.status == WakeModelStatus.Error) "重试" else "安装")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
