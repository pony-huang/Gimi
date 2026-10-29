package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 获取候选与勾选分离；确认只更新草稿，保存服务后才进入聊天模型目录。 */
@Composable
fun LLMModelManagementSection(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    val keys = state.candidates.mapTo(mutableSetOf()) { it.key }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.modelsettings_model_selection_count, keys.size, state.pendingModelKeys.intersect(keys).size),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { onAction(LLMModelSettingDetailAction.SelectAllModels) },
            enabled = state.canEdit && keys.any { it !in state.pendingModelKeys }) {
            Text(stringResource(R.string.modelsettings_select_all))
        }
        TextButton(onClick = { onAction(LLMModelSettingDetailAction.ClearModels) },
            enabled = state.canEdit && state.pendingModelKeys.isNotEmpty()) {
            Text(stringResource(R.string.modelsettings_clear_selection))
        }
    }
    if (state.candidates.isEmpty()) PreferenceSectionNote(stringResource(R.string.modelsettings_no_model_candidates))
    NewModelKind.entries.forEach { kind ->
        val models = state.candidates.filter { row -> when (kind) {
            NewModelKind.Chat -> !row.model.isStt && !row.model.isTts
            NewModelKind.Stt -> row.model.isStt
            NewModelKind.Tts -> row.model.isTts
        } }
        if (models.isNotEmpty()) {
            PreferenceSectionTitle(stringResource(R.string.modelsettings_model_type_count, stringResource(kind.labelRes), models.size))
            PreferenceGroupCard {
                models.forEachIndexed { index, row ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                        value = row.key in state.pendingModelKeys,
                        enabled = state.canEdit,
                        role = Role.Checkbox,
                        onValueChange = { onAction(LLMModelSettingDetailAction.ToggleModel(row.key)) },
                    ).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.key in state.pendingModelKeys, enabled = state.canEdit, onCheckedChange = null)
                        Text(row.model.id, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (index < models.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (state.isAddDialogVisible) AddModelDialog(state, onAction)
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
                NewModelKind.entries.forEach { kind ->
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
    AsssistantaiTheme { LLMModelSettingDetailScreen(modelSelectionPreviewState(selectedCount), {}, { _, _ -> }) }
}

/** 对话和两类语音模型的固定预览数据，不调用远端目录。 */
internal fun modelSelectionPreviewState(selectedCount: Int): LLMModelSettingDetailUiState {
    val service = modelServicePreviewFixture().copy(id = "openai", name = "OpenAI", groups = listOf(
        ModelGroup("chat", "Chat", listOf(Model("gpt-4.1", "gpt-4.1"), Model("gpt-4.1-mini", "gpt-4.1-mini"))),
        ModelGroup("stt", "STT", listOf(Model("gpt-4o-transcribe", "gpt-4o-transcribe", isStt = true), Model("whisper-1", "whisper-1", isStt = true))),
        ModelGroup("tts", "TTS", listOf(Model("gpt-4o-mini-tts", "gpt-4o-mini-tts", isTts = true), Model("tts-1", "tts-1", isTts = true))),
    ))
    val rows = service.groups.flatMap { group -> group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) } }
    return LLMModelSettingDetailUiState(isLoading = false, service = service, baseline = service,
        page = ServiceEditorPage.Models, candidates = rows, pendingModelKeys = rows.take(selectedCount).mapTo(mutableSetOf()) { it.key })
}

/** 覆盖未选、部分选择、全选三种批量操作状态。 */
class ModelSelectionPreviewProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(0, 2, 6)
}
