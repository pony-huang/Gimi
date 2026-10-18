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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 已完成轮次的步骤摘要。
 *
 * 默认只占一行；展开后展示用户可理解的步骤名称与工具状态，不展示原始思考文本或参数。
 */
@Composable
internal fun TurnActivityGroupPanel(
    segment: TurnSegment.Activity,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasThought = segment.entries.any { it is TimelineEntry.Thought }
    val toolCalls = segment.entries.filterIsInstance<TimelineEntry.ToolCall>()
    val steps = buildList {
        if (hasThought) add(ActivityStep.Thought)
        toolCalls.forEach { add(ActivityStep.Tool(it)) }
    }
    if (steps.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .heightIn(min = 36.dp)
                .padding(horizontal = 2.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.chat_timeline_completed_steps, steps.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.chat_timeline_collapse else R.string.chat_timeline_expand,
                ),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
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
                        ActivityStepRow(step)
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
private fun ActivityStepRow(step: ActivityStep) {
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
            icon = { ToolStatusIcon(step.call.status) },
            title = toolActionTitle(step.call.name),
            detail = toolStatusLabel(step.call.status),
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
