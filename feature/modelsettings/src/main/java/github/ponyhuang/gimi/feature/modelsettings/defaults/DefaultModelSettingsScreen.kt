package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.PickerSingleChoiceDialog
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun DefaultModelSettingsScreen(
    state: DefaultModelSettingsUiState,
    onAction: (DefaultModelSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked) {
                item {
                    Text(
                        text = stringResource(R.string.modelsettings_agent_mutation_blocked),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_defaults_section_chat)) }
            item {
                DefaultModelOption(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.modelsettings_defaults_assistant_title),
                    subtitle = stringResource(R.string.modelsettings_defaults_assistant_subtitle),
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
                    title = stringResource(R.string.modelsettings_defaults_quick_title),
                    subtitle = stringResource(R.string.modelsettings_defaults_quick_subtitle),
                    selection = state.fastSelection,
                    rows = state.chatModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Fast)) }
                    },
                )
            }
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_defaults_section_voice)) }
            item {
                DefaultModelOption(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.modelsettings_defaults_stt_title),
                    subtitle = stringResource(R.string.modelsettings_defaults_stt_subtitle),
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
                    title = stringResource(R.string.modelsettings_defaults_tts_title),
                    subtitle = stringResource(R.string.modelsettings_defaults_tts_subtitle),
                    selection = state.ttsSelection,
                    rows = state.ttsModels,
                    onClick = if (state.isMutationBlocked) null else {
                        { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Tts)) }
                    },
                )
            }
            item {
                val voice = state.ttsVoiceOptions.firstOrNull { it.id == state.ttsVoiceId }
                val enabled = state.ttsModels.any { it.selection() == state.ttsSelection }
                PreferenceListItem(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.modelsettings_defaults_voice_title),
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
            title = stringResource(R.string.modelsettings_dialog_pick_assistant),
            target = DefaultModelDialog.Assistant,
            onAction = onAction,
        )
        DefaultModelDialog.Fast -> ModelChoiceDialog(
            rows = state.chatModels,
            currentSelection = state.fastSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_quick),
            target = DefaultModelDialog.Fast,
            onAction = onAction,
        )
        DefaultModelDialog.Speech -> ModelChoiceDialog(
            rows = state.speechModels,
            currentSelection = state.speechSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_stt),
            target = DefaultModelDialog.Speech,
            onAction = onAction,
        )
        DefaultModelDialog.Tts -> ModelChoiceDialog(
            rows = state.ttsModels,
            currentSelection = state.ttsSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_tts),
            target = DefaultModelDialog.Tts,
            onAction = onAction,
        )
        DefaultModelDialog.TtsVoice -> PickerSingleChoiceDialog(
            options = state.ttsVoiceOptions,
            selected = { it.id == state.ttsVoiceId },
            key = TtsVoice::id,
            title = stringResource(R.string.modelsettings_dialog_pick_voice),
            optionTitle = TtsVoice::name,
            optionSubtitle = { listOfNotNull(it.language, it.gender).joinToString(" · ") },
            emptyText = stringResource(R.string.modelsettings_dialog_no_voices),
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
    PreferenceListItem(
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
        // 与聊天页模型弹窗同一规则：服务内只有一个候选组时，组名对每行都相同，只显示服务名。
        optionSubtitle = { row ->
            val groupCount = rows.asSequence()
                .filter { it.service.id == row.service.id }
                .map { it.group.id }
                .distinct()
                .count()
            if (groupCount > 1) {
                "${row.service.name} · ${row.group.name}"
            } else {
                row.service.name
            }
        },
        emptyText = stringResource(R.string.modelsettings_dialog_no_models),
        onPick = {
            onAction(DefaultModelSettingsAction.SelectModel(target, it.selection()))
        },
        onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
    )
}
