package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 获取候选与勾选分离；确认只更新草稿，保存服务后才进入聊天模型目录。 */
@Composable
fun LLMModelManagementSection(state: LLMModelSettingDetailUiState, onAction: (LLMModelSettingDetailAction) -> Unit) {
    PreferenceSectionNote(stringResource(R.string.modelsettings_model_selection_hint))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { onAction(LLMModelSettingDetailAction.RefreshModels) },
            enabled = state.canEdit && !state.isRefreshing && !state.service?.apiKey.isNullOrBlank()) {
            Text(stringResource(if (state.isRefreshing) R.string.modelsettings_sync_refreshing else R.string.modelsettings_fetch_models))
        }
        TextButton(onClick = { onAction(LLMModelSettingDetailAction.ShowAddDialog) }, enabled = state.canEdit) {
            Text(stringResource(R.string.modelsettings_manual_model))
        }
    }
    OutlinedTextField(state.modelQuery, { onAction(LLMModelSettingDetailAction.ModelQueryChanged(it)) },
        placeholder = { Text(stringResource(R.string.modelsettings_search_model)) },
        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    val query = state.modelQuery.trim()
    val visible = state.candidates.filter { query.isBlank() || it.model.id.contains(query, true) || it.model.name.contains(query, true) }
    if (visible.isEmpty()) PreferenceSectionNote(stringResource(R.string.modelsettings_no_matches))
    NewModelKind.entries.forEach { kind ->
        val models = visible.filter { row -> when (kind) {
            NewModelKind.Chat -> !row.model.isStt && !row.model.isTts
            NewModelKind.Stt -> row.model.isStt
            NewModelKind.Tts -> row.model.isTts
        } }
        if (models.isNotEmpty()) {
            PreferenceSectionTitle(stringResource(kind.labelRes))
            PreferenceGroupCard {
                models.forEach { row ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                        value = row.key in state.pendingModelKeys,
                        enabled = state.canEdit,
                        role = Role.Checkbox,
                        onValueChange = { onAction(LLMModelSettingDetailAction.ToggleModel(row.key)) },
                    ).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(row.model.id, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Checkbox(checked = row.key in state.pendingModelKeys, enabled = state.canEdit, onCheckedChange = null)
                    }
                }
            }
        }
    }
    PreferenceSectionNote(stringResource(R.string.modelsettings_fetch_hint))
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
private fun ModelSelectionPreview() {
    val service = modelServicePreviewFixture()
    val rows = service.groups.flatMap { group -> group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) } }
    AsssistantaiTheme { LLMModelSettingDetailScreen(LLMModelSettingDetailUiState(isLoading = false,
        service = service, baseline = service, page = ServiceEditorPage.Models, candidates = rows,
        pendingModelKeys = rows.mapTo(mutableSetOf()) { it.key }), {}, { _, _ -> }) }
}
