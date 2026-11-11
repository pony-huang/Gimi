package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 获取候选与勾选分离；确认只更新草稿，保存服务后才进入聊天模型目录。 */
@Composable
fun LLMModelManagementSection(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    val keys = state.candidates.mapTo(mutableSetOf()) { it.key }
    val selectedCount = state.pendingModelKeys.intersect(keys).size
    val groups = state.allowedModelKinds.map { kind ->
        // Realtime 类型没有能力标记,匹配无任何级联能力的模型;其余按能力集合求交集。
        kind to state.candidates.filter {
            if (kind.capabilities.isEmpty()) it.model.capabilities.isEmpty()
            else kind.capabilities.any { capability -> capability in it.model.capabilities }
        }
    }.filter { (_, models) -> models.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.modelsettings_model_selection_count, keys.size, selectedCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onAction(LLMModelSettingDetailAction.SelectAllModels) },
            enabled = state.canEdit && selectedCount < keys.size,
        ) { Text(stringResource(R.string.modelsettings_select_all)) }
        if (selectedCount > 0) {
            TextButton(
                onClick = { onAction(LLMModelSettingDetailAction.ClearModels) },
                enabled = state.canEdit,
            ) { Text(stringResource(R.string.modelsettings_clear_selection)) }
        }
    }
    if (state.candidates.isEmpty()) PreferenceSectionNote(stringResource(R.string.modelsettings_no_model_candidates))
    groups.forEach { (kind, models) ->
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.modelsettings_model_type_count, modelCapabilityLabel(kind), models.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (groups.size > 1) {
                TextButton(
                    onClick = {
                        // 复用单项选择入口，只补选本组尚未选中的模型，不影响其他分组。
                        models.filter { it.key !in state.pendingModelKeys }.forEach {
                            onAction(LLMModelSettingDetailAction.ToggleModel(it.key))
                        }
                    },
                    enabled = state.canEdit && models.any { it.key !in state.pendingModelKeys },
                ) { Text(stringResource(R.string.modelsettings_select_group)) }
            }
        }
        PreferenceGroupCard {
            models.forEachIndexed { index, row ->
                val selected = row.key in state.pendingModelKeys
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                        .heightIn(min = 72.dp)
                        .toggleable(
                            value = selected,
                            enabled = state.canEdit,
                            role = Role.Checkbox,
                            onValueChange = { onAction(LLMModelSettingDetailAction.ToggleModel(row.key)) },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, enabled = state.canEdit, onCheckedChange = null)
                    ModelSelectionName(
                        model = row.model,
                        kind = kind,
                        isQwenAudio = state.service?.id == "qwen-audio",
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                if (index < models.lastIndex) HorizontalDivider(
                    Modifier.padding(start = 64.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
    if (state.isAddDialogVisible) AddModelDialog(state, onAction)
}

/** 能力的中文说明与协议缩写并列，帮助非开发者辨认分组。 */
@Composable
private fun modelCapabilityLabel(kind: NewModelKind): String = stringResource(when (kind) {
    NewModelKind.Chat -> R.string.modelsettings_selection_kind_llm
    NewModelKind.Stt -> R.string.modelsettings_selection_kind_stt
    NewModelKind.Asr -> R.string.modelsettings_selection_kind_asr
    NewModelKind.Tts -> R.string.modelsettings_selection_kind_tts
    NewModelKind.Realtime -> R.string.modelsettings_model_kind_realtime
})

// 仅美化已知的 Qwen Audio 命名格式；未知或自定义名称原样展示，不推断模型能力。
private val QwenAudioModelId = Regex("qwen-audio-([0-9]+\\.[0-9]+)-(asr|tts)-(flash|plus)(?:-(message|streaming))?")

@Composable
private fun ModelSelectionName(model: Model, kind: NewModelKind, isQwenAudio: Boolean, modifier: Modifier = Modifier) {
    val qwen = if (isQwenAudio && (model.name.isBlank() || model.name == model.id)) QwenAudioModelId.matchEntire(model.id) else null
    val title = if (qwen != null) {
        stringResource(R.string.modelsettings_qwen_audio_name, qwen.groupValues[1],
            stringResource(if (qwen.groupValues[3] == "flash") R.string.modelsettings_model_flash else R.string.modelsettings_model_plus))
    } else model.name.ifBlank { model.id }
    val mode = when (qwen?.groupValues?.get(4)) {
        "message" -> R.string.modelsettings_model_short_speech
        "streaming" -> R.string.modelsettings_model_streaming
        else -> null
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            if (mode != null) stringResource(R.string.modelsettings_model_description, modelCapabilityLabel(kind), stringResource(mode))
            else modelCapabilityLabel(kind),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (title != model.id) Text(
            model.id,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddModelDialog(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(LLMModelSettingDetailAction.DismissAddDialog) },
        title = { Text(stringResource(R.string.modelsettings_dialog_add_custom_model_title)) },
        text = {
            Column {
                OutlinedTextField(state.newModelId, { onAction(LLMModelSettingDetailAction.NewLLMModelIdChanged(it)) },
                    label = { Text(stringResource(R.string.modelsettings_dialog_model_id)) }, singleLine = true,
                    enabled = state.canEdit)
                state.allowedModelKinds.forEach { kind ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = kind == state.newModelKind,
                        enabled = state.canEdit, role = Role.RadioButton,
                        onClick = { onAction(LLMModelSettingDetailAction.NewLLMModelKindChanged(kind)) })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == state.newModelKind, onClick = null, enabled = state.canEdit)
                        Text(stringResource(kind.labelRes), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(LLMModelSettingDetailAction.ConfirmAddLLMModel) },
                enabled = state.canEdit && state.newModelId.isNotBlank()) {
                Text(stringResource(R.string.modelsettings_dialog_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { onAction(LLMModelSettingDetailAction.DismissAddDialog) }) {
            Text(stringResource(R.string.modelsettings_dialog_cancel))
        } },
    )
}

@Preview(name = "模型选择", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "模型选择 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelSelectionPreview(@PreviewParameter(ModelSelectionPreviewProvider::class) selectedCount: Int) {
    GimiTheme { LLMModelSettingDetailScreen(modelSelectionPreviewState(selectedCount), {}, { _, _ -> }) }
}

/** 对话和两类语音模型的固定预览数据，不调用远端目录。 */
internal fun modelSelectionPreviewState(selectedCount: Int): LLMModelSettingDetailUiState {
    val service = modelServicePreviewFixture().copy(id = "openai", name = "OpenAI", groups = listOf(
        ModelGroup("chat", "Chat", listOf(Model("gpt-4.1", "gpt-4.1"), Model("gpt-4.1-mini", "gpt-4.1-mini"))),
        ModelGroup("stt", "STT", listOf(Model("gpt-4o-transcribe", "gpt-4o-transcribe", capabilities = setOf(ModelCapability.STT)), Model("whisper-1", "whisper-1", capabilities = setOf(ModelCapability.STT)))),
        ModelGroup("tts", "TTS", listOf(Model("gpt-4o-mini-tts", "gpt-4o-mini-tts", capabilities = setOf(ModelCapability.TTS)), Model("tts-1", "tts-1", capabilities = setOf(ModelCapability.TTS)))),
    ))
    val llmService = service.copy(groups = service.groups.filter { it.id == "chat" })
    val rows = llmService.groups.flatMap { group -> group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) } }
    return LLMModelSettingDetailUiState(isLoading = false, service = llmService, baseline = llmService,
        page = ServiceEditorPage.Models, candidates = rows, pendingModelKeys = rows.take(selectedCount).mapTo(mutableSetOf()) { it.key })
}

/** 覆盖未选、部分选择、全选三种批量操作状态。 */
class ModelSelectionPreviewProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(0, 1, 2)
}

@Preview(name = "语音模型选择", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "语音模型选择 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "语音模型选择 · 窄屏大字", widthDp = 320, heightDp = 844, fontScale = 1.3f, locale = "zh")
@Composable
private fun SpeechModelSelectionPreview(@PreviewParameter(ModelSelectionPreviewProvider::class) selectedCount: Int) {
    val stt = listOf("qwen-audio-3.1-asr-flash-message", "qwen-audio-3.1-asr-flash-streaming", "qwen-audio-3.0-asr-flash-streaming")
    val tts = listOf("qwen-audio-3.1-tts-flash", "qwen-audio-3.1-tts-plus", "qwen-audio-3.0-tts-flash", "qwen-audio-3.0-tts-plus")
    val service = modelServicePreviewFixture().copy(
        id = "qwen-audio", name = "Qwen", kind = ModelServiceKind.Speech,
        groups = listOf(
            ModelGroup("stt", "STT", stt.map { Model(it, it, capabilities = setOf(ModelCapability.STT)) }),
            ModelGroup("tts", "TTS", tts.map { Model(it, it, capabilities = setOf(ModelCapability.TTS)) }),
        ),
    )
    val rows = service.groups.flatMap { group -> group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) } }
    val selected = when (selectedCount) { 0 -> emptySet(); 1 -> rows.take(2).mapTo(mutableSetOf()) { it.key }; else -> rows.mapTo(mutableSetOf()) { it.key } }
    GimiTheme {
        LLMModelSettingDetailScreen(
            LLMModelSettingDetailUiState(isLoading = false, service = service, baseline = service,
                page = ServiceEditorPage.Models, candidates = rows, pendingModelKeys = selected),
            {}, { _, _ -> },
        )
    }
}

@Preview(name = "LLM 展示名与长 ID · 大字", widthDp = 320, heightDp = 844, fontScale = 1.3f, locale = "zh")
@Preview(name = "LLM English", widthDp = 390, heightDp = 844, locale = "en")
@Composable
private fun NamedModelSelectionPreview() {
    val state = modelSelectionPreviewState(0)
    val models = listOf(Model("gpt-4.1-mini", "GPT-4.1 Mini"), Model("custom-provider/model-with-a-long-identifier-2026-11", "自定义推理模型"))
    val rows = models.map { LLMModelSettingDetailRow.LLMModelItem("chat", it) }
    GimiTheme {
        LLMModelSettingDetailScreen(state.copy(candidates = rows, pendingModelKeys = setOf(rows.first().key)), {}, { _, _ -> })
    }
}
