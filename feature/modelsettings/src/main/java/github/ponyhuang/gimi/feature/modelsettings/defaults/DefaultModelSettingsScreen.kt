package github.ponyhuang.gimi.feature.modelsettings.defaults

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.SearchablePickerSingleChoiceDialog
import github.ponyhuang.gimi.ui.components.VoicePickerDialog
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.GimiTheme

@Composable
fun DefaultModelSettingsScreen(
    state: DefaultModelSettingsUiState,
    onAction: (DefaultModelSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    /** 从选择器空态进入添加服务；保存后宿主回到本页并再次打开该用途选择器。 */
    onAddService: (DefaultModelDialog) -> Unit = {},
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
            item { PreferenceSectionTitle(stringResource(R.string.modelsettings_defaults_section_realtime)) }
            item {
                PreferenceGroupCard {
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = stringResource(R.string.modelsettings_defaults_realtime_model_title),
                        selection = state.realtimeSelection,
                        rows = state.realtimeModels,
                        showDivider = true,
                        onClick = if (state.isMutationBlocked) null else {
                            { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.RealtimeModel)) }
                        },
                    )

                    val realtimeEnabled = state.realtimeModels.any { it.selection() == state.realtimeSelection }
                    val realtimeVoiceLabel = when {
                        !realtimeEnabled -> ""
                        state.realtimeVoiceId != null -> {
                            val v = state.realtimeVoiceOptions.firstOrNull { it.id == state.realtimeVoiceId }
                            v?.name ?: state.realtimeVoiceId
                        }
                        state.realtimeDefaultVoiceName.isNotBlank() -> {
                            stringResource(R.string.modelsettings_realtime_default_voice_format, state.realtimeDefaultVoiceName)
                        }
                        else -> ""
                    }
                    PreferenceListItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.modelsettings_defaults_voice_title),
                        subtitle = realtimeVoiceLabel,
                        showDivider = false,
                        onClick = if (realtimeEnabled && !state.isMutationBlocked) {
                            { onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.RealtimeVoice)) }
                        } else {
                            null
                        },
                        iconContainer = Color.Transparent,
                        iconTint = if (realtimeEnabled) {
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
            onAddService = onAddService,
        )

        DefaultModelDialog.Fast -> ModelChoiceDialog(
            rows = state.chatModels,
            currentSelection = state.fastSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_quick),
            target = DefaultModelDialog.Fast,
            onAction = onAction,
            onAddService = onAddService,
        )

        DefaultModelDialog.Speech -> ModelChoiceDialog(
            rows = state.speechModels,
            currentSelection = state.speechSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_stt),
            target = DefaultModelDialog.Speech,
            onAction = onAction,
            onAddService = onAddService,
        )

        DefaultModelDialog.Tts -> ModelChoiceDialog(
            rows = state.ttsModels,
            currentSelection = state.ttsSelection,
            title = stringResource(R.string.modelsettings_dialog_pick_tts),
            target = DefaultModelDialog.Tts,
            onAction = onAction,
            onAddService = onAddService,
        )

        DefaultModelDialog.TtsVoice -> {
            // 音色选择统一走对话框：搜索、分组、性别筛选与「手动输入音色 ID」都在同一屏里，
            // 与模型选择共用同一套选择对话框外壳。
            VoicePickerDialog(
                title = stringResource(R.string.modelsettings_dialog_pick_voice),
                currentVoiceId = state.ttsVoiceId,
                options = state.ttsVoiceOptions,
                isLoading = state.isTtsVoiceLoading,
                onPick = { onAction(DefaultModelSettingsAction.SelectVoice(it)) },
                onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
            )
        }

        DefaultModelDialog.RealtimeModel -> RealtimeModelChoiceDialog(
            rows = state.realtimeModels,
            currentSelection = state.realtimeSelection,
            onAction = onAction,
            onAddService = onAddService,
        )

        DefaultModelDialog.RealtimeVoice -> {
            VoicePickerDialog(
                title = stringResource(R.string.modelsettings_dialog_pick_voice),
                currentVoiceId = state.realtimeActiveVoiceId,
                options = state.realtimeVoiceOptions,
                defaultVoiceId = state.realtimeDefaultVoiceId,
                isLoading = state.isRealtimeVoiceLoading,
                onPick = { onAction(DefaultModelSettingsAction.SelectRealtimeVoice(it)) },
                onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
            )
        }

        null -> Unit
    }
}

/**
 * Realtime 默认模型的选择弹窗：只列真实候选模型。
 *
 * 不提供「不使用 Realtime」占位项——没有候选模型就是没有：候选为空时展示空态与补配服务入口，
 * 未配置状态由「没有选中任何候选」表达，不需要用户主动清除。
 */
@Composable
private fun RealtimeModelChoiceDialog(
    rows: List<SelectableModelRow>,
    currentSelection: ModelSelection?,
    onAction: (DefaultModelSettingsAction) -> Unit,
    onAddService: (DefaultModelDialog) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchablePickerSingleChoiceDialog(
        query = query,
        onQueryChange = { query = it },
        searchText = { "${it.model.name} ${it.model.id} ${it.instance.name}" },
        searchPlaceholder = stringResource(R.string.modelsettings_search_model_or_service),
        options = rows,
        selected = { it.selection() == currentSelection },
        key = { "${it.instance.id}/${it.connection.id}/${it.model.id}" },
        title = stringResource(R.string.modelsettings_dialog_pick_realtime_model),
        optionTitle = { it.model.name },
        optionSubtitle = { it.instance.name },
        emptyText = stringResource(R.string.modelsettings_dialog_no_models),
        onPick = { onAction(DefaultModelSettingsAction.SelectRealtimeModel(it.selection())) },
        onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
        emptyContent = {
            EmptyCatalogWithAddService {
                onAction(DefaultModelSettingsAction.DismissDialog)
                onAddService(DefaultModelDialog.RealtimeModel)
            }
        },
    )
}

@Composable
private fun DefaultModelOption(
    icon: ImageVector,
    title: String,
    selection: ModelSelection?,
    rows: List<SelectableModelRow>,
    onClick: (() -> Unit)?,
    showDivider: Boolean = false,
    subtitle: String? = null,
) {
    val selected = rows.firstOrNull { it.selection() == selection }
    PreferenceListItem(
        icon = icon,
        title = title,
        subtitle = selected?.let { "${it.model.name} · ${it.instance.name}" } ?: subtitle,
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
    onAddService: (DefaultModelDialog) -> Unit,
) {
    var query by rememberSaveable(target) { mutableStateOf("") }
    // 能力名与"未接入"说明在组合函数里取好：optionSubtitle 不是 @Composable lambda，
    // 不能在 lambda 内部再取字符串资源。
    val sttLabel = stringResource(R.string.modelsettings_capability_stt)
    val asrLabel = stringResource(R.string.modelsettings_capability_asr)
    val ttsLabel = stringResource(R.string.modelsettings_capability_tts)
    val capabilityLabels = mapOf(
        ModelCapability.STT to sttLabel,
        ModelCapability.ASR to asrLabel,
        ModelCapability.TTS to ttsLabel,
    )
    val unsupportedMessages = mapOf(
        ModelCapability.STT to stringResource(R.string.modelsettings_model_capability_not_supported, sttLabel),
        ModelCapability.ASR to stringResource(R.string.modelsettings_model_capability_not_supported, asrLabel),
        ModelCapability.TTS to stringResource(R.string.modelsettings_model_capability_not_supported, ttsLabel),
    )
    // 按能力分组：专用转写（STT）与音频理解（ASR）是两套协议语义，必须排在一起，
    // 否则用户只能靠模型名去猜选中的是哪一类。
    val groupedRows = remember(rows) { rows.sortedBy { it.capability?.ordinal ?: 0 } }
    SearchablePickerSingleChoiceDialog(
        query = query,
        onQueryChange = { query = it },
        searchText = { "${it.model.name} ${it.model.id} ${it.instance.name}" },
        searchPlaceholder = stringResource(R.string.modelsettings_search_model_or_service),
        options = groupedRows,
        selected = { it.selection() == currentSelection },
        key = { "${it.instance.id}/${it.connection.id}/${it.model.id}" },
        title = title,
        optionTitle = { it.model.name },
        // 与聊天页模型弹窗同一规则：服务内只有一个候选组时，组名对每行都相同，只显示服务名。
        optionSubtitle = { row ->
            buildList {
                row.capability?.let { capabilityLabels[it] }?.let { label -> add(label) }
                add(row.instance.name)
                // 未接入的能力仍然显示，但禁用并说明原因，避免用户误以为配置失败。
                if (!row.isSupported) {
                    row.capability?.let { unsupportedMessages[it] }?.let { note -> add(note) }
                }
            }.joinToString(" · ")
        },
        // 服务未接入该能力时不可选中。
        enabled = { it.isSupported },
        emptyText = stringResource(R.string.modelsettings_dialog_no_models),
        onPick = {
            onAction(DefaultModelSettingsAction.SelectModel(target, it.selection()))
        },
        onDismiss = { onAction(DefaultModelSettingsAction.DismissDialog) },
        // 目录为空时提供补配服务的行动出口；保存后回到本页再次确认一次用途。
        emptyContent = {
            EmptyCatalogWithAddService {
                onAction(DefaultModelSettingsAction.DismissDialog)
                onAddService(target)
            }
        },
    )
}

@Composable
private fun EmptyCatalogWithAddService(onAddService: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.modelsettings_dialog_no_models), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onAddService) {
            Text(stringResource(R.string.modelsettings_dialog_add_service))
        }
    }
}

private fun defaultModelSettingsPreviewState(isMutationBlocked: Boolean): DefaultModelSettingsUiState {
    fun previewInstance(
        id: String,
        name: String,
        templateId: String,
        capability: github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability,
        vararg modelIds: String,
    ): github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance {
        val models = modelIds.map { modelId ->
            Model(
                id = modelId,
                name = modelId,
                capabilities = when (capability) {
                    github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Realtime ->
                        setOf(ModelCapability.Realtime)
                    else -> setOf(ModelCapability.LLM)
                },
            )
        }
        return github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance(
            id = id,
            name = name,
            templateId = templateId,
            isEnabled = true,
            isOfficialToolsEnabled = true,
            sharedApiKey = "sk-test",
            connections = listOf(
                github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection(
                    id = "$id-connection",
                    capability = capability,
                    protocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions,
                    endpoint = "https://api.openai.com/v1",
                    models = models,
                ),
            ),
        )
    }
    val instance = previewInstance(
        "instance-openai", "OpenAI", "openai",
        github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Conversation,
        "gpt-4o",
    )
    val row = SelectableModelRow(
        instance = instance,
        connection = instance.connections.first(),
        model = instance.connections.first().models.first(),
    )
    val realtimeInstance = previewInstance(
        "instance-qwen", "Qwen", "qwen",
        github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability.Realtime,
        "qwen-audio-3.1-realtime-plus",
    )
    val realtimeRow = SelectableModelRow(
        instance = realtimeInstance,
        connection = realtimeInstance.connections.first(),
        model = realtimeInstance.connections.first().models.first(),
        capability = ModelCapability.Realtime,
    )
    return DefaultModelSettingsUiState(
        assistantSelection = row.selection(),
        speechSelection = row.selection(),
        ttsSelection = row.selection(),
        ttsVoiceId = "default",
        chatModels = listOf(row),
        speechModels = listOf(row),
        ttsModels = listOf(row),
        realtimeSelection = realtimeRow.selection(),
        realtimeModels = listOf(realtimeRow),
        isMutationBlocked = isMutationBlocked,
    )
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultModelSettingsScreenPreview() {
    GimiTheme {
        DefaultModelSettingsScreen(
            state = defaultModelSettingsPreviewState(isMutationBlocked = false),
            onAction = {})
    }
}

@Preview(name = "Realtime 未配置", showBackground = true)
@Preview(name = "Realtime 未配置 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultModelSettingsScreenRealtimeUnsetPreview() {
    // 未配置 Realtime 模型：Realtime 链路不可进入，候选列表展示空态。
    GimiTheme {
        DefaultModelSettingsScreen(
            state = defaultModelSettingsPreviewState(isMutationBlocked = false).copy(
                realtimeSelection = null,
            ),
            onAction = {})
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultModelSettingsScreenBlockedPreview() {
    // 阻塞态：Agent 运行中禁改配置，顶部改为统一的信息横幅。
    GimiTheme {
        DefaultModelSettingsScreen(
            state = defaultModelSettingsPreviewState(isMutationBlocked = true),
            onAction = {})
    }
}
