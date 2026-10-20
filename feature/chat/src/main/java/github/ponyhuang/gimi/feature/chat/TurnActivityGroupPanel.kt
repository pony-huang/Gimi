package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.delay

/**
 * 当前轮次的紧凑运行胶囊与可展开工具步骤。
 *
 * 默认只占一行；展开后展示用户可理解的步骤名称与工具状态，不展示原始思考文本或参数。
 */
@Composable
internal fun TurnActivityGroupPanel(
    segment: TurnSegment.Activity,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    runStatus: ChatRunStatus? = null,
    isRunning: Boolean = false,
    startedAtMillis: Long = 0L,
    endedAtMillis: Long = startedAtMillis,
    showElapsed: Boolean = false,
) {
    val hasThought = segment.entries.any { it is TimelineEntry.Thought }
    val toolCalls = segment.entries.filterIsInstance<TimelineEntry.ToolCall>()
    val steps = buildList {
        if (hasThought) add(ActivityStep.Thought)
        toolCalls.forEach { add(ActivityStep.Tool(it)) }
    }
    if (steps.isEmpty() && runStatus == null) return
    var now by remember(startedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    // 仅活跃轮次需要刷新时钟；切换会话或结束运行时自动取消刷新。
    LaunchedEffect(isRunning, startedAtMillis, showElapsed) {
        while (isRunning && showElapsed) {
            now = System.currentTimeMillis()
            delay(100L)
        }
    }
    val elapsed = ((if (isRunning) now else endedAtMillis) - startedAtMillis).coerceAtLeast(0L)
    val completed = !isRunning && toolCalls.all { it.status == ToolCallStatus.Completed }
    val label = if (runStatus != null) runStatusLabel(runStatus) else stringResource(
        if (completed) R.string.chat_timeline_completed_steps else R.string.chat_timeline_steps,
        steps.size,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggle)
                    .heightIn(min = 30.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (runStatus != null) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = label,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showElapsed) {
                    Text(
                        text = stringResource(R.string.chat_timeline_elapsed, elapsed / 1000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.chat_timeline_collapse else R.string.chat_timeline_expand,
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded && steps.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    steps.forEach { step ->
                        ActivityStepRow(step, animateRunning = runStatus != null)
                    }
                }
            }
        }
    }
}

/** 展开摘要中的一项：合并后的思考阶段，或一次具体工具调用。 */
private sealed interface ActivityStep {
    /** 多条思考分段合并成一项通用说明。 */
    data object Thought : ActivityStep

    /**
     * 一次工具调用。
     *
     * @property call 保留原始调用状态以生成动作名称和完成状态。
     */
    data class Tool(val call: TimelineEntry.ToolCall) : ActivityStep
}

@Composable
private fun ActivityStepRow(step: ActivityStep, animateRunning: Boolean) {
    when (step) {
        ActivityStep.Thought -> ActivityStepContent(
            icon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            },
            title = stringResource(R.string.chat_timeline_step_understand),
            detail = stringResource(R.string.chat_timeline_tool_completed),
        )
        is ActivityStep.Tool -> ActivityStepContent(
            icon = {
                if (step.call.status == ToolCallStatus.Running && !animateRunning) {
                    Icon(Icons.Default.QuestionMark, null, Modifier.size(17.dp))
                } else ToolStatusIcon(step.call.status)
            },
            title = step.call.name,
            detail = toolStatusLabel(
                if (step.call.status == ToolCallStatus.Running && !animateRunning) ToolCallStatus.Unknown
                else step.call.status,
            ),
        )
    }
}

@Composable
private fun ActivityStepContent(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolStatusIcon(status: ToolCallStatus) {
    when (status) {
        ToolCallStatus.Running -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        ToolCallStatus.Completed -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        ToolCallStatus.Rejected -> Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        ToolCallStatus.AwaitingConfirmation -> Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        ToolCallStatus.Unknown -> Icon(
            imageVector = Icons.Default.QuestionMark,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
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
        TimelineEntry.Thought("thought", "内部思考文本只保留在数据层，不进入摘要。"),
        TimelineEntry.ToolCall(
            callId = "now-playing",
            name = "spotify_now_playing",
            argsSummary = "(device=phone)",
            status = ToolCallStatus.Completed,
        ),
        TimelineEntry.ToolCall(
            callId = "next",
            name = "spotify_next",
            argsSummary = "",
            status = ToolCallStatus.Completed,
        ),
    ),
)

@Preview(name = "步骤摘要折叠", showBackground = true)
@Composable
private fun TurnActivityCollapsedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnActivityGroupPanel(previewActivityGroup, false, {})
    }
}

@Preview(name = "步骤摘要展开", showBackground = true)
@Composable
private fun TurnActivityExpandedPreview() {
    AsssistantaiTheme(darkTheme = false) {
        TurnActivityGroupPanel(previewActivityGroup, true, {})
    }
}

@Preview(name = "步骤摘要展开（深色）", showBackground = true)
@Composable
private fun TurnActivityExpandedDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        TurnActivityGroupPanel(previewActivityGroup, true, {})
    }
}

@Preview(name = "运行胶囊 · 收起", showBackground = true)
@Composable
private fun TurnActivityRunningPreview() {
    AsssistantaiTheme {
        TurnActivityGroupPanel(
            segment = previewActivityGroup,
            expanded = false,
            onToggle = {},
            runStatus = ChatRunStatus.UsingTool("web_search"),
            startedAtMillis = 1000L,
            endedAtMillis = 4200L,
            showElapsed = true,
        )
    }
}

@Preview(name = "运行胶囊 · 展开深色", showBackground = true)
@Composable
private fun TurnActivityRunningDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        TurnActivityGroupPanel(
            segment = previewActivityGroup.copy(entries = previewActivityGroup.entries + TimelineEntry.ToolCall(
                callId = "search", name = "web_search", argsSummary = "", status = ToolCallStatus.Running,
            )),
            expanded = true,
            onToggle = {},
            runStatus = ChatRunStatus.UsingTool("web_search"),
            startedAtMillis = 1000L,
            endedAtMillis = 4200L,
            showElapsed = true,
        )
    }
}
