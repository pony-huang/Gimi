package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard

/**
 * 「模型」管理区：节标题行（刷新 / 新增动作）+ 每个模型分组一张 One UI 卡片。
 *
 * 分组头为卡片内普通行（承担折叠入口，保持 ≥56dp 点击热区），
 * 模型行为纯文本行 + 能力徽标 + 移除按钮，行间使用内缩分隔线。
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

        // 扁平行流按 GroupHeader 切段，每段渲染成一张可折叠卡片。
        rows.toModelGroups().forEachIndexed { index, group ->
            PreferenceGroupCard(
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
            ) {
                GroupHeaderRow(
                    row = group.header,
                    hasModels = group.models.isNotEmpty(),
                    onToggle = {
                        onAction(LLMModelSettingDetailAction.ToggleGroup(group.header.groupId))
                    },
                )
                group.models.forEachIndexed { modelIndex, item ->
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
                        showDivider = modelIndex < group.models.lastIndex,
                    )
                }
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

/**
 * 一次展开渲染的模型分组片段。
 *
 * @property header 分组头行，承担折叠入口与分组名展示。
 * @property models 组内模型行，按目录顺序排列。
 */
private data class ModelGroup(
    val header: LLMModelSettingDetailRow.GroupHeader,
    val models: List<LLMModelSettingDetailRow.LLMModelItem>,
)

/** 把 ViewModel 展开的扁平行流按分组头重新切段，供逐组渲染卡片。 */
private fun List<LLMModelSettingDetailRow>.toModelGroups(): List<ModelGroup> = buildList {
    var header: LLMModelSettingDetailRow.GroupHeader? = null
    var models = mutableListOf<LLMModelSettingDetailRow.LLMModelItem>()
    for (row in this@toModelGroups) {
        when (row) {
            is LLMModelSettingDetailRow.GroupHeader -> {
                header?.let { add(ModelGroup(it, models)) }
                header = row
                models = mutableListOf()
            }
            is LLMModelSettingDetailRow.LLMModelItem -> models.add(row)
        }
    }
    header?.let { add(ModelGroup(it, models)) }
}

@Composable
private fun GroupHeaderRow(
    row: LLMModelSettingDetailRow.GroupHeader,
    hasModels: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (row.isExpanded) 180f else 0f,
        label = "arrow-rotation",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        // 组头承担折叠入口，保持 ≥56dp 的完整点击热区（见 LLMModelManagementSectionTest）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (row.isExpanded) R.string.modelsettings_sync_collapse
                    else R.string.modelsettings_sync_expand,
                ),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(22.dp)
                    .rotate(rotation),
            )
        }
        if (hasModels) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
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
