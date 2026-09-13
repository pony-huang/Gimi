package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.delay

/**
 * 单个 assistant 轮次的无状态活动时间线。
 *
 * 运行中的轮次由调用方保持展开；本组件只在头部局部按秒更新时间，过程条目与文件结果
 * 都收纳在同一个圆角容器内。
 */
@Composable
internal fun TurnTimelinePanel(
    timeline: TurnTimeline,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenLocalFile: (LocalFileReference) -> Unit,
    onShowAllLocalFiles: (responseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember(timeline.turnId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timeline.turnId, timeline.startedAtMs, timeline.isRunning) {
        while (timeline.isRunning) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val durationMs = timeline.startedAtMs?.let { startedAt ->
        val endedAt = if (timeline.isRunning) nowMs else timeline.finishedAtMs
        endedAt?.minus(startedAt)?.takeIf { it > 0L }
    }
    val header = if (durationMs == null) {
        stringResource(R.string.chat_timeline_step_count, timeline.entries.size)
    } else {
        stringResource(R.string.chat_timeline_worked_duration, formatTimelineDuration(durationMs))
    }

    Surface(
        onClick = onToggle,
        enabled = !timeline.isRunning,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        timeline.isFailed -> Icons.Default.ErrorOutline
                        timeline.isRunning -> Icons.Default.HourglassTop
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.chat_timeline_collapse else R.string.chat_timeline_expand,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded && timeline.entries.isNotEmpty()) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                    )
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        timeline.entries.forEach { entry ->
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

@Composable
private fun formatTimelineDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        stringResource(R.string.chat_timeline_duration_minutes_seconds, minutes, seconds)
    } else {
        stringResource(R.string.chat_timeline_duration_seconds, seconds)
    }
}

private fun previewTimeline(isRunning: Boolean, failed: Boolean = false) = TurnTimeline(
    turnId = if (isRunning) "running" else "complete",
    startedAtMs = System.currentTimeMillis() - 74_000L,
    finishedAtMs = if (isRunning) null else System.currentTimeMillis(),
    isRunning = isRunning,
    isFailed = failed,
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
    answerMessages = emptyList(),
)

@Preview(name = "折叠", showBackground = true)
@Composable
private fun TurnTimelineCollapsedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnTimelinePanel(previewTimeline(false), false, {}, {}, {})
    }
}

@Preview(name = "展开", showBackground = true)
@Composable
private fun TurnTimelineExpandedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnTimelinePanel(previewTimeline(false), true, {}, {}, {})
    }
}

@Preview(name = "运行中（深色）", showBackground = true)
@Composable
private fun TurnTimelineRunningDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        TurnTimelinePanel(previewTimeline(true), true, {}, {}, {})
    }
}
