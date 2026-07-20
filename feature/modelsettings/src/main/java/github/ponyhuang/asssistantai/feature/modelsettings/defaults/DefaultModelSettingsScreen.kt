package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.speech.model.MiMoTtsVoices
import github.ponyhuang.asssistantai.domain.speech.model.TtsVoice
import github.ponyhuang.asssistantai.ui.common.PickerSingleChoiceDialog
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer

@Composable
fun DefaultModelSettingsScreen(
    state: DefaultModelSettingsUiState,
    onAction: (DefaultModelSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked || state.notice != null) {
                item {
                    Text(
                        text = state.notice ?: "Agent 任务进行中，请先停止任务后再修改。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                DefaultModelOption(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "默认助手模型",
                    subtitle = "创建新助手时使用的模型",
                    selection = state.assistantSelection,
                    rows = state.chatModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Assistant)) }
                    },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.Default.FlashOn,
                    title = "快速模型",
                    subtitle = "用于需要快速响应的助手功能",
                    selection = state.fastSelection,
                    rows = state.chatModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Fast)) }
                    },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.Default.Mic,
                    title = "默认语音模型",
                    subtitle = "用于语音输入，仅显示语音识别模型",
                    selection = state.speechSelection,
                    rows = state.speechModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Speech)) }
                    },
                )
            }
            item {
                DefaultModelOption(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "默认语音播放模型",
                    subtitle = "用于朗读助手回复，仅显示语音合成模型",
                    selection = state.ttsSelection,
                    rows = state.ttsModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Tts)) }
                    },
                )
            }
            item {
                val voice = MiMoTtsVoices.all.firstOrNull { it.id == state.ttsVoiceId }
                val enabled = state.ttsModels.any { it.selection() == state.ttsSelection }
                SettingsListItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "语音播放音色",
                    subtitle = voice?.name ?: state.ttsVoiceId,
                    onClick = if (enabled && !state.isMutationBlocked) {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.TtsVoice)) }
                    } else {
                        null
                    },
                    iconTint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }

    when (state.dialog) {
        DefaultModelDialog.Assistant -> ModelChoiceDialog(
            rows = state.chatModels,
            currentSelection = state.assistantSelection,
            title = "选择默认助手模型",
            target = DefaultModelDialog.Assistant,
            onAction = onAction,
        )
        DefaultModelDialog.Fast -> ModelChoiceDialog(
            rows = state.chatModels,
            currentSelection = state.fastSelection,
            title = "选择快速模型",
            target = DefaultModelDialog.Fast,
            onAction = onAction,
        )
        DefaultModelDialog.Speech -> ModelChoiceDialog(
            rows = state.speechModels,
            currentSelection = state.speechSelection,
            title = "选择默认语音模型",
            target = DefaultModelDialog.Speech,
            onAction = onAction,
        )
        DefaultModelDialog.Tts -> ModelChoiceDialog(
            rows = state.ttsModels,
            currentSelection = state.ttsSelection,
            title = "选择默认语音播放模型",
            target = DefaultModelDialog.Tts,
            onAction = onAction,
        )
        DefaultModelDialog.TtsVoice -> PickerSingleChoiceDialog(
            options = MiMoTtsVoices.all,
            selected = { it.id == state.ttsVoiceId },
            key = TtsVoice::id,
            title = "选择语音播放音色",
            optionTitle = TtsVoice::name,
            optionSubtitle = { listOfNotNull(it.language, it.gender).joinToString(" · ") },
            emptyText = "暂无可用音色",
            onPick = { onAction(DefaultModelSettingsAction.SelectVoice(it.id)) },
            onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun DefaultModelOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selection: ModelSelection?,
    rows: List<SelectableModelRow>,
    onClick: (() -> Unit)?,
) {
    val selected = rows.firstOrNull { it.selection() == selection }
    SettingsListItem(
        icon = icon,
        title = title,
        subtitle = selected?.let { "${it.model.name} · ${it.service.name}" } ?: subtitle,
        onClick = onClick,
    )
}

@Composable
private fun ModelChoiceDialog(
    rows: List<SelectableModelRow>,
    currentSelection: ModelSelection?,
    title: String,
    target: DefaultModelDialog,
    onAction: (DefaultModelSettingsAction) -> Unit,
) {
    PickerSingleChoiceDialog(
        options = rows,
        selected = { it.selection() == currentSelection },
        key = { "${it.service.id}/${it.group.id}/${it.model.id}" },
        title = title,
        optionTitle = { it.model.name },
        optionSubtitle = { "${it.service.name} · ${it.group.name}" },
        emptyText = "暂无可用模型，请先在模型服务中启用至少一个模型。",
        onPick = {
            onAction(DefaultModelSettingsAction.SelectModel(target, it.selection()))
        },
        onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
    )
}
