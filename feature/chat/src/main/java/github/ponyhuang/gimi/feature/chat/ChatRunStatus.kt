package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 输入框上方正在进行的处理状态。
 *
 * 只表达用户可理解的当前动作，不携带原始思考文本或工具参数。
 */
internal sealed interface ChatRunStatus {
    /** 模型正在理解请求或继续推理。 */
    data object Thinking : ChatRunStatus

    /**
     * 正在执行一个工具。
     *
     * @property toolName 工具协议名，仅用于本地化动作文案。
     */
    data class UsingTool(val toolName: String) : ChatRunStatus

    /** 工具已结束，模型正在整理最终回复。 */
    data object PreparingAnswer : ChatRunStatus
}

/**
 * 从当前聊天状态派生输入框上方的单条运行状态。
 *
 * 最终回答开始流式输出后不再展示文字状态，避免与正在增长的正文重复；需要用户确认或
 * 输入的操作面板会占据输入区，此时也隐藏运行状态。
 */
internal fun ChatUiState.currentRunStatus(): ChatRunStatus? {
    if (!isAgentRunning || pendingComposerAction != null || listItems.isEmpty()) return null
    val timeline = (listItems.lastOrNull() as? ChatListItem.AssistantTurn)?.timeline
        ?: return ChatRunStatus.Thinking
    if (!timeline.isRunning) return null

    val runningTool = timeline.segments.asReversed()
        .filterIsInstance<TurnSegment.Activity>()
        .asSequence()
        .flatMap { it.entries.asReversed().asSequence() }
        .filterIsInstance<TimelineEntry.ToolCall>()
        .firstOrNull { it.status == ToolCallStatus.Running }
    if (runningTool != null) return ChatRunStatus.UsingTool(runningTool.name)

    return when (val lastSegment = timeline.segments.lastOrNull()) {
        is TurnSegment.Answer -> {
            // partial 正文自身就是进度反馈；非 partial 时通常只剩最后收尾。
            if (lastSegment.message.partial) null else ChatRunStatus.PreparingAnswer
        }
        else -> ChatRunStatus.Thinking
    }
}

/**
 * 把工具协议名映射为面向用户的动作名。
 *
 * 未识别的工具统一退化为「执行工具」，避免把内部函数名直接暴露给用户。
 */
@Composable
internal fun toolActionTitle(toolName: String): String {
    val normalized = toolName.lowercase()
    return when {
        "now_playing" in normalized -> stringResource(R.string.chat_process_tool_now_playing)
        "next" in normalized || "previous" in normalized || "skip_to" in normalized ->
            stringResource(R.string.chat_process_tool_change_track)
        "pause" in normalized -> stringResource(R.string.chat_process_tool_pause)
        "play" in normalized -> stringResource(R.string.chat_process_tool_play)
        "search" in normalized -> stringResource(R.string.chat_process_tool_search)
        "read" in normalized || "open_file" in normalized || "get_file" in normalized ->
            stringResource(R.string.chat_process_tool_read)
        "calendar" in normalized -> stringResource(R.string.chat_process_tool_calendar)
        else -> stringResource(R.string.chat_process_tool_generic)
    }
}

/**
 * 输入框上方的轻量运行状态行。
 *
 * 状态文字使用单行省略，三点动画直接跟在文案后，不再单独占用一行。
 */
@Composable
internal fun ChatRunStatusRow(
    status: ChatRunStatus,
    modifier: Modifier = Modifier,
) {
    val icon = when (status) {
        ChatRunStatus.Thinking -> Icons.Default.Psychology
        is ChatRunStatus.UsingTool -> Icons.Default.Build
        ChatRunStatus.PreparingAnswer -> Icons.Default.AutoAwesome
    }
    val label = when (status) {
        ChatRunStatus.Thinking -> stringResource(R.string.chat_process_status_thinking)
        is ChatRunStatus.UsingTool -> stringResource(
            R.string.chat_process_status_tool,
            toolActionTitle(status.toolName),
        )
        ChatRunStatus.PreparingAnswer -> stringResource(R.string.chat_process_status_preparing)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        AnimatedDots()
    }
}

@Preview(name = "运行状态 · 思考", showBackground = true, widthDp = 360)
@Composable
private fun ThinkingStatusPreview() {
    AsssistantaiTheme {
        ChatRunStatusRow(ChatRunStatus.Thinking)
    }
}

@Preview(name = "运行状态 · 工具", showBackground = true, widthDp = 360)
@Composable
private fun UsingToolStatusPreview() {
    AsssistantaiTheme {
        ChatRunStatusRow(ChatRunStatus.UsingTool("spotify_now_playing"))
    }
}
