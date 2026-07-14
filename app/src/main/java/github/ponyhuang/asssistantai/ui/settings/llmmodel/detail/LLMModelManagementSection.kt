package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.data.LLMModelProvider
import kotlinx.coroutines.launch

/**
 * 动态模型管理区：控制栏 + 扁平列表（普通 Column，**禁止**用 LazyColumn —
 * 详情页外层是 Column(verticalScroll)，嵌套 LazyColumn 会触发 Compose 非法约束崩溃）。
 */
@Composable
fun LLMModelManagementSection(
    service: LLMModelProvider,
    rows: List<LLMModelRow>,
    onToggleGroup: (String) -> Unit,
    onRemoveModel: (groupId: String, modelId: String) -> Unit,
    onAppendModel: (String) -> Unit,
    onRefreshRemote: suspend () -> LLMModelRefreshResult,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    val totalCount = service.LLMModelGroups.sumOf { it.models.size }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 控制栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "模型 ($totalCount)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = service.apiKey.isNotBlank() && !refreshing,
                onClick = {
                    refreshing = true
                    scope.launch {
                        when (val result = onRefreshRemote()) {
                            is LLMModelRefreshResult.Success -> {
                                Toast.makeText(
                                    context,
                                    "已同步远端：${result.modelIds.size} 条",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            LLMModelRefreshResult.Failure -> {
                                Toast.makeText(context, "同步远端模型失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        refreshing = false
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (refreshing) "同步中..." else "刷新",
                )
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "添加模型")
            }
        }

        // 扁平列表（普通 Column — 行数很少，无需 LazyColumn；且不允许嵌套在外层 verticalScroll 内）
        rows.forEach { row ->
            when (row) {
                is LLMModelRow.GroupHeader -> GroupHeaderRow(
                    row = row,
                    onToggle = { onToggleGroup(row.groupId) },
                )
                is LLMModelRow.LLMModelItemRow -> ModelItemRow(
                    row = row,
                    onRemove = { onRemoveModel(row.groupId, row.item.modelId) },
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }

    if (showAddDialog) {
        AddModelDialog(
            onConfirm = { id ->
                onAppendModel(id)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

private fun rowKey(row: LLMModelRow): String = when (row) {
    is LLMModelRow.GroupHeader -> "header-${row.groupId}"
    is LLMModelRow.LLMModelItemRow -> "item-${row.groupId}-${row.item.modelId}"
}

@Composable
private fun GroupHeaderRow(
    row: LLMModelRow.GroupHeader,
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
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
        Text(
            text = row.groupName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelItemRow(
    row: LLMModelRow.LLMModelItemRow,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Construction,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = row.item.modelId,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("custom-model") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义模型") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("模型 ID") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (input.isNotBlank()) onConfirm(input.trim())
            }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
