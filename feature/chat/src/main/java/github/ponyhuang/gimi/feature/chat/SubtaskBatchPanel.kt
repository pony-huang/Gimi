package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.SubtaskBatch
import github.ponyhuang.gimi.domain.conversation.model.SubtaskRun
import github.ponyhuang.gimi.domain.conversation.model.SubtaskStatus
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 原聊天 timeline 内的无状态任务树；展开意图交回 ViewModel，正文只由主助手输出。 */
@Composable
internal fun SubtaskBatchPanel(batch: SubtaskBatch, expanded: Boolean, onToggle: () -> Unit) {
    val completed = batch.tasks.count { it.status == SubtaskStatus.COMPLETED }
    val running = batch.tasks.any { it.status == SubtaskStatus.RUNNING || it.status == SubtaskStatus.QUEUED }
    val failed = batch.tasks.any { it.status == SubtaskStatus.FAILED }
    val label = when {
        running -> stringResource(R.string.chat_subtasks_running, completed, batch.tasks.size)
        failed -> stringResource(R.string.chat_subtasks_failed, batch.tasks.count { it.status == SubtaskStatus.FAILED })
        batch.tasks.any { it.status == SubtaskStatus.CANCELLED } -> stringResource(R.string.chat_subtasks_stopped, completed, batch.tasks.size)
        batch.tasks.any { it.status == SubtaskStatus.INTERRUPTED } -> stringResource(R.string.chat_subtasks_interrupted)
        else -> stringResource(R.string.chat_subtasks_completed, completed)
    }
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background,
        border = if (expanded) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)) else null,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.AccountTree,
                    null, Modifier.size(17.dp),
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                val end = batch.tasks.mapNotNull { it.endedAt ?: it.startedAt }.maxOrNull() ?: batch.createdAt
                Text(stringResource(R.string.chat_subtasks_elapsed, ((end - batch.createdAt) / 1000).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(if (expanded) R.string.chat_timeline_collapse else R.string.chat_timeline_expand),
                    Modifier.size(17.dp))
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    batch.tasks.forEach { SubtaskRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SubtaskRow(task: SubtaskRun) {
    val tint = when (task.status) {
        SubtaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
        SubtaskStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        if (task.status == SubtaskStatus.RUNNING) CircularProgressIndicator(Modifier.padding(top = 2.dp).size(16.dp), strokeWidth = 1.8.dp)
        else Icon(when (task.status) {
            SubtaskStatus.COMPLETED -> Icons.Outlined.CheckCircle
            SubtaskStatus.FAILED, SubtaskStatus.INTERRUPTED -> Icons.Outlined.ErrorOutline
            SubtaskStatus.CANCELLED -> Icons.Outlined.Close
            else -> Icons.Outlined.Schedule
        }, null, Modifier.padding(top = 2.dp).size(17.dp), tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(when (task.status) {
                    SubtaskStatus.QUEUED -> R.string.chat_subtask_queued
                    SubtaskStatus.RUNNING -> R.string.chat_subtask_running
                    SubtaskStatus.COMPLETED -> R.string.chat_subtask_completed
                    SubtaskStatus.FAILED -> R.string.chat_subtask_failed
                    SubtaskStatus.CANCELLED -> R.string.chat_subtask_cancelled
                    SubtaskStatus.INTERRUPTED -> R.string.chat_subtask_interrupted
                }), style = MaterialTheme.typography.labelSmall, color = tint)
            }
            Text(task.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (task.result.isNotBlank()) Text(task.result.take(200), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (task.status == SubtaskStatus.FAILED) Text(stringResource(
                when (task.error) {
                    "TIMEOUT" -> R.string.chat_subtask_timeout
                    "PARENT_READ_REQUIRED" -> R.string.chat_subtask_parent_read
                    else -> R.string.chat_subtask_error
                },
            ), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            task.tools.forEach { step ->
                Text(stringResource(if (step.completed) R.string.chat_subtask_tool_completed else R.string.chat_subtask_tool_started, step.name),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun previewBatch() = SubtaskBatch("preview", "session", "invocation", "call", 0, listOf(
    SubtaskRun("1", "梳理项目背景", "快速模型", SubtaskStatus.COMPLETED, result = "提炼了 3 项项目目标", endedAt = 5000),
    SubtaskRun("2", "对比实施方案", "快速模型", SubtaskStatus.RUNNING, startedAt = 1000),
    SubtaskRun("3", "核对行动建议", "聊天模型", SubtaskStatus.QUEUED),
))

@Preview(name = "子任务展开 · 浅色", showBackground = true, widthDp = 393)
@Composable
private fun SubtasksLightPreview() = AsssistantaiTheme { SubtaskBatchPanel(previewBatch(), true, {}) }

@Preview(name = "子任务展开 · 深色", showBackground = true, widthDp = 393)
@Composable
private fun SubtasksDarkPreview() = AsssistantaiTheme(darkTheme = true) { SubtaskBatchPanel(previewBatch(), true, {}) }

@Preview(name = "子任务收起", showBackground = true, widthDp = 393)
@Composable
private fun SubtasksCollapsedPreview() = AsssistantaiTheme { SubtaskBatchPanel(previewBatch(), false, {}) }

@Preview(name = "子任务失败与取消", showBackground = true, widthDp = 393)
@Composable
private fun SubtasksFailurePreview() = AsssistantaiTheme { SubtaskBatchPanel(previewBatch().copy(tasks = listOf(
    SubtaskRun("1", "核对交付计划", "快速模型", SubtaskStatus.FAILED, error = "TIMEOUT"),
    SubtaskRun("2", "整理风险清单", "快速模型", SubtaskStatus.CANCELLED),
    SubtaskRun("3", "检查资料覆盖", "快速模型", SubtaskStatus.INTERRUPTED),
)), true, {}) }
