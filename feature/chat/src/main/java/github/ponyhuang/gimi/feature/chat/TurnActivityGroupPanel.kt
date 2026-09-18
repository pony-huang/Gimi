package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * assistant 轮次内单个工具活动组的折叠行。
 *
 * 头部显示「执行工具 N 次」（N 为组内工具调用数；纯思考组退化为「思考」），
 * 展开后按事件顺序显示思考文本、工具调用与文件结果。运行中的组由调用方保持展开。
 */
@Composable
internal fun TurnActivityGroupPanel(
    segment: TurnSegment.Activity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenLocalFile: (LocalFileReference) -> Unit,
    onShowAllLocalFiles: (responseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolCount = segment.entries.count { it is TimelineEntry.ToolCall }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    if (expanded) R.string.chat_timeline_collapse else R.string.chat_timeline_expand,
                ),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (toolCount > 0) {
                    stringResource(R.string.chat_timeline_tool_group_count, toolCount)
                } else {
                    stringResource(R.string.chat_timeline_thought_group)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                segment.entries.forEach { entry ->
                    TimelineEntryRow(
                        entry = entry,
                        onOpenLocalFile = onOpenLocalFile,
                        onShowAllLocalFiles = onShowAllLocalFiles,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryRow(
    entry: TimelineEntry,
    onOpenLocalFile: (LocalFileReference) -> Unit,
    onShowAllLocalFiles: (responseId: String) -> Unit,
) {
    when (entry) {
        is TimelineEntry.Thought -> TimelineTextRow(
            icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
            title = entry.text,
        )
        is TimelineEntry.ToolCall -> TimelineTextRow(
            icon = { ToolStatusIcon(entry.status) },
            title = "${toolDisplayName(entry.name)}${entry.argsSummary}",
            detail = toolStatusLabel(entry.status),
        )
        is TimelineEntry.FileResults -> {
            val result = entry.response.localFileSearchResult ?: return
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TimelineTextRow(
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    title = stringResource(R.string.chat_timeline_file_count, result.files.size),
                )
                LocalFileSearchCarousel(
                    responseId = entry.response.id,
                    result = result,
                    onOpenFile = onOpenLocalFile,
                    onShowAll = onShowAllLocalFiles,
                )
            }
        }
    }
}

@Composable
private fun TimelineTextRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolStatusIcon(status: ToolCallStatus) {
    when (status) {
        ToolCallStatus.Running -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        ToolCallStatus.Completed -> Icon(Icons.Default.CheckCircle, contentDescription = null)
        ToolCallStatus.Rejected -> Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        ToolCallStatus.AwaitingConfirmation -> Icon(Icons.Default.Shield, contentDescription = null)
        ToolCallStatus.Unknown -> Icon(Icons.Default.QuestionMark, contentDescription = null)
    }
}

@Composable
private fun toolStatusLabel(status: ToolCallStatus): String = stringResource(
    when (status) {
        ToolCallStatus.Running -> R.string.chat_timeline_tool_running
        ToolCallStatus.Completed -> R.string.chat_timeline_tool_completed
        ToolCallStatus.Rejected -> R.string.chat_timeline_tool_rejected
        ToolCallStatus.AwaitingConfirmation -> R.string.chat_timeline_tool_awaiting_confirmation
        ToolCallStatus.Unknown -> R.string.chat_timeline_tool_unknown
    },
)

private val previewActivityGroup = TurnSegment.Activity(
    id = "preview:a0",
    entries = listOf(
        TimelineEntry.Thought("thought", "先检索工作区内的项目计划"),
        TimelineEntry.ToolCall("call", "search_workspace", "(query=项目计划)", ToolCallStatus.Completed),
        TimelineEntry.FileResults(
            FunctionResponseView(
                id = "call",
                name = "search_workspace",
                localFileSearchResult = LocalFileSearchResult(
                    query = "项目计划",
                    files = listOf(
                        LocalFileReference(
                            displayName = "项目计划.md",
                            mimeType = "text/markdown",
                            sizeBytes = 1L,
                            modifiedTimeMillis = 1L,
                            category = "document",
                            contentUri = "content://preview/project-plan",
                        ),
                    ),
                ),
            ),
        ),
        TimelineEntry.ToolCall("read", "read_document", "(风险清单.pdf)", ToolCallStatus.Rejected),
    ),
)

@Preview(name = "工具组折叠", showBackground = true)
@Composable
private fun TurnActivityGroupCollapsedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnActivityGroupPanel(previewActivityGroup, false, {}, {}, {})
    }
}

@Preview(name = "工具组展开", showBackground = true)
@Composable
private fun TurnActivityGroupExpandedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnActivityGroupPanel(previewActivityGroup, true, {}, {}, {})
    }
}

@Preview(name = "工具组展开（深色）", showBackground = true)
@Composable
private fun TurnActivityGroupExpandedDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        TurnActivityGroupPanel(previewActivityGroup, true, {}, {}, {})
    }
}
