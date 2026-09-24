package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard

/**
 * 「模型」管理区：刷新后按目录顺序平铺模型，避免来源分组和折叠增加查找成本。
 */
@Composable
fun LLMModelManagementSection(
    service: LLMModelSetting,
    rows: List<LLMModelSettingDetailRow>,
    isRefreshing: Boolean,
    isAddDialogVisible: Boolean,
    newModelId: String,
    newModelKind: NewModelKind,
    onAction: (LLMModelSettingDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.modelsettings_section_models),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = service.apiKey.isNotBlank() && !isRefreshing,
                onClick = { onAction(LLMModelSettingDetailAction.RefreshModels) },
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(
                        if (isRefreshing) R.string.modelsettings_sync_refreshing
                        else R.string.modelsettings_sync_refresh,
                    ),
                )
            }
            IconButton(onClick = { onAction(LLMModelSettingDetailAction.ShowAddDialog) }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.modelsettings_dialog_add_custom_model_title),
                )
            }
        }

        PreferenceGroupCard {
            val models = rows.filterIsInstance<LLMModelSettingDetailRow.LLMModelItem>()
            models.forEachIndexed { modelIndex, item ->
                ModelItemRow(
                    row = item,
                    onRemove = {
                        onAction(
                            LLMModelSettingDetailAction.RemoveLLMModel(
                                groupId = item.groupId,
                                modelId = item.model.id,
                            ),
                        )
                    },
                    showDivider = modelIndex < models.lastIndex,
                )
            }
        }
    }

    if (isAddDialogVisible) {
        AddModelDialog(
            input = newModelId,
            kind = newModelKind,
            onInputChange = {
                onAction(LLMModelSettingDetailAction.NewLLMModelIdChanged(it))
            },
            onKindChange = {
                onAction(LLMModelSettingDetailAction.NewLLMModelKindChanged(it))
            },
            onConfirm = { onAction(LLMModelSettingDetailAction.ConfirmAddLLMModel) },
            onDismiss = { onAction(LLMModelSettingDetailAction.DismissAddDialog) },
        )
    }
}


@Composable
private fun ModelItemRow(
    row: LLMModelSettingDetailRow.LLMModelItem,
    onRemove: () -> Unit,
    showDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.model.id,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.model.isStt) {
                ModelKindChip(label = "STT")
            }
            if (row.model.isTts) {
                ModelKindChip(label = "TTS")
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.modelsettings_remove_model),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

/** 模型能力徽标：标示模型在语音链路中的角色（语音识别 / 语音合成）。 */
@Composable
private fun ModelKindChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AddModelDialog(
    input: String,
    kind: NewModelKind,
    onInputChange: (String) -> Unit,
    onKindChange: (NewModelKind) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modelsettings_dialog_add_custom_model_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.modelsettings_dialog_model_id)) },
                )
                NewModelKind.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onKindChange(option) }
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = kind == option,
                            onClick = { onKindChange(option) },
                        )
                        Text(stringResource(option.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.modelsettings_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.modelsettings_dialog_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun LLMModelManagementSectionPreview() {
    AsssistantaiTheme {
        val service = LLMModelSetting(
            id = "openai",
            name = "OpenAI",
            isEnabled = true,
            apiKey = "sk-test",
            apiBaseUrl = "https://api.openai.com/v1",
            apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Standard,
            anthropicBaseUrl = "https://api.anthropic.com",
            groups = listOf(
                github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup(
                    id = "gpt",
                    name = "GPT",
                    models = listOf(
                        github.ponyhuang.gimi.domain.modelcatalog.model.Model(
                            id = "gpt-4o",
                            name = "GPT-4o",
                        ),
                    ),
                ),
            ),
        )
        val model = service.groups.first().models.first()
        LLMModelManagementSection(
            service = service,
            rows = listOf(
                LLMModelSettingDetailRow.LLMModelItem(
                    groupId = service.groups.first().id,
                    model = model,
                ),
            ),
            isRefreshing = false,
            isAddDialogVisible = false,
            newModelId = "custom-model",
            newModelKind = NewModelKind.Chat,
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LLMModelManagementSectionAddDialogPreview() {
    AsssistantaiTheme {
        LLMModelManagementSection(
            service = LLMModelSetting(
                id = "openai",
                name = "OpenAI",
                isEnabled = true,
                apiKey = "sk-test",
                apiBaseUrl = "https://api.openai.com/v1",
                apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Standard,
                anthropicBaseUrl = "https://api.anthropic.com",
                groups = emptyList(),
            ),
            rows = emptyList(),
            isRefreshing = false,
            isAddDialogVisible = true,
            newModelId = "my-custom-model",
            newModelKind = NewModelKind.Chat,
            onAction = {},
        )
    }
}
