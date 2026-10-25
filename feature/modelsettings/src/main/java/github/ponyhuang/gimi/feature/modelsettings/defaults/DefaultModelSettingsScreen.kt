package github.ponyhuang.gimi.feature.modelsettings.defaults

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.SearchablePickerSingleChoiceDialog
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun DefaultModelSettingsScreen(
    state: DefaultModelSettingsUiState,
    onAction: (DefaultModelSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.isMutationBlocked) {
                item { PreferenceBanner(stringResource(R.string.modelsettings_agent_mutation_blocked)) }
            }
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_model_kind_chat)) }
            item {
                PreferenceGroupCard {
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = stringResource(R.string.modelsettings_defaults_assistant_title),
                        subtitle = stringResource(R.string.modelsettings_defaults_assistant_subtitle),
                        selection = state.assistantSelection,
                        rows = state.chatModels,
                        showDivider = true,
                        onClick = if (state.isMutationBlocked) null else {
                            { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Assistant)) }
                        },
                    )
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
            }
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_model_kind_stt)) }
            item {
                PreferenceGroupCard {
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
            }
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_model_kind_tts)) }
            item {
                PreferenceGroupCard {
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = stringResource(R.string.modelsettings_defaults_tts_title),
                        subtitle = stringResource(R.string.modelsettings_defaults_tts_subtitle),
                        selection = state.ttsSelection,
                        rows = state.ttsModels,
                        showDivider = true,
                        onClick = if (state.isMutationBlocked) null else {
                            { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Tts)) }
                        },
                    )
                    val voice = state.ttsVoiceOptions.firstOrNull { it.id == state.ttsVoiceId }
                    val enabled = state.ttsModels.any { it.selection() == state.ttsSelection }
                    PreferenceListItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.modelsettings_defaults_voice_title),
                        subtitle = voice?.name ?: state.ttsVoiceId,
                        showDivider = false,
                        onClick = if (enabled && !state.isMutationBlocked) {
                            { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.TtsVoice)) }
                        } else {
                            null
                        },
                        iconContainer = Color.Transparent,
                        iconTint = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
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

        DefaultModelDialog.TtsVoice -> {
            var query by rememberSaveable(state.ttsSelection) { mutableStateOf("") }
            SearchablePickerSingleChoiceDialog(
                query = query,
                onQueryChange = { query = it },
                searchText = {
                    listOfNotNull(it.name, it.id, it.description, it.language, it.gender)
                        .joinToString(" ")
                },
                searchPlaceholder = stringResource(R.string.modelsettings_search_voice),
                options = state.ttsVoiceOptions,
                selected = { it.id == state.ttsVoiceId },
                key = TtsVoice::id,
                title = stringResource(R.string.modelsettings_dialog_pick_voice),
                optionTitle = TtsVoice::name,
                optionSubtitle = {
                    listOfNotNull(it.description, it.language, it.gender)
                        .filter { text -> text.isNotBlank() }
                        .joinToString(" · ")
                },
                emptyText = stringResource(R.string.modelsettings_dialog_no_voices),
                onPick = { onAction(DefaultModelSettingsAction.SelectVoice(it.id)) },
                onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
            )
        }

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
    showDivider: Boolean = false,
) {
    val selected = rows.firstOrNull { it.selection() == selection }
    PreferenceListItem(
        icon = icon,
        title = title,
        subtitle = selected?.let { "${it.model.name} · ${it.service.name}" } ?: subtitle,
        onClick = onClick,
        showDivider = showDivider,
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
    var query by rememberSaveable(target) { mutableStateOf("") }
    SearchablePickerSingleChoiceDialog(
        query = query,
        onQueryChange = { query = it },
        searchText = { "${it.model.name} ${it.model.id} ${it.service.name} ${it.group.name}" },
        searchPlaceholder = stringResource(R.string.modelsettings_search_model_or_service),
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

private fun defaultModelSettingsPreviewState(isMutationBlocked: Boolean): DefaultModelSettingsUiState {
    val service = LLMModelSetting(
        id = "openai",
        name = "OpenAI",
        isEnabled = true,
        apiKey = "sk-test",
        apiBaseUrl = "https://api.openai.com/v1",
        apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "https://api.anthropic.com",
        groups = listOf(
            ModelGroup(
                id = "gpt",
                name = "GPT",
                models = listOf(Model(id = "gpt-4o", name = "GPT-4o")),
            ),
        ),
    )
    val row = SelectableModelRow(
        service = service,
        group = service.groups.first(),
        model = service.groups.first().models.first(),
    )
    return DefaultModelSettingsUiState(
        assistantSelection = row.selection(),
        speechSelection = row.selection(),
        ttsSelection = row.selection(),
        ttsVoiceId = "default",
        chatModels = listOf(row),
        speechModels = listOf(row),
        ttsModels = listOf(row),
        isMutationBlocked = isMutationBlocked,
    )
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultModelSettingsScreenPreview() {
    AsssistantaiTheme {
        DefaultModelSettingsScreen(
            state = defaultModelSettingsPreviewState(isMutationBlocked = false),
            onAction = {})
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultModelSettingsScreenBlockedPreview() {
    // 阻塞态：Agent 运行中禁改配置，顶部改为统一的信息横幅。
    AsssistantaiTheme {
        DefaultModelSettingsScreen(
            state = defaultModelSettingsPreviewState(isMutationBlocked = true),
            onAction = {})
    }
}
