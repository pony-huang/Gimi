package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService

@Composable
fun LLMModelManagementSection(
    service: ModelService,
    rows: List<ModelServiceDetailRow>,
    isRefreshing: Boolean,
    isAddDialogVisible: Boolean,
    newModelId: String,
    newModelKind: NewModelKind,
    onAction: (ModelServiceDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = service.apiKey.isNotBlank() && !isRefreshing,
                onClick = { onAction(ModelServiceDetailAction.RefreshModels) },
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (isRefreshing) "同步中..." else "刷新",
                )
            }
            IconButton(onClick = { onAction(ModelServiceDetailAction.ShowAddDialog) }) {
                Icon(Icons.Default.Add, contentDescription = "添加模型")
            }
        }

        rows.forEach { row ->
            when (row) {
                is ModelServiceDetailRow.GroupHeader -> GroupHeaderRow(
                    row = row,
                    onToggle = {
                        onAction(ModelServiceDetailAction.ToggleGroup(row.groupId))
                    },
                )
                is ModelServiceDetailRow.ModelItem -> ModelItemRow(
                    row = row,
                    onRemove = {
                        onAction(
                            ModelServiceDetailAction.RemoveModel(
                                groupId = row.groupId,
                                modelId = row.model.id,
                            ),
                        )
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }

    if (isAddDialogVisible) {
        AddModelDialog(
            input = newModelId,
            kind = newModelKind,
            onInputChange = {
                onAction(ModelServiceDetailAction.NewModelIdChanged(it))
            },
            onKindChange = {
                onAction(ModelServiceDetailAction.NewModelKindChanged(it))
            },
            onConfirm = { onAction(ModelServiceDetailAction.ConfirmAddModel) },
            onDismiss = { onAction(ModelServiceDetailAction.DismissAddDialog) },
        )
    }
}

@Composable
private fun GroupHeaderRow(
    row: ModelServiceDetailRow.GroupHeader,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (row.isExpanded) 90f else 0f,
        label = "arrow-rotation",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (row.isExpanded) "收起" else "展开",
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
        Text(
            text = row.groupName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelItemRow(
    row: ModelServiceDetailRow.ModelItem,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Construction,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = row.model.id,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.model.isStt) {
            Text(
                text = "STT",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (row.model.isTts) {
            Text(
                text = "TTS",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "移除模型",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
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
        title = { Text("添加自定义模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    label = { Text("模型 ID") },
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
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
