package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 工具时间链中正在进行的处理状态。
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
 * 从当前聊天状态派生工具时间链中的单条运行状态。
 *
 * 最终回答开始流式输出后不再展示文字状态，避免与正在增长的正文重复；需要用户确认或
 * 输入的操作面板会占据输入区，此时也隐藏运行状态。
 */
internal fun ChatUiState.currentRunStatus(): ChatRunStatus? {
    if (!isAgentRunning || pendingComposerAction != null || listItems.isEmpty()) return null
    val timeline = (listItems.lastOrNull() as? ChatListItem.AssistantTurn)?.timeline
        ?: return ChatRunStatus.Thinking
    if (!timeline.isRunning) return null
    if ((timeline.segments.lastOrNull() as? TurnSegment.Answer)?.message?.partial == true) return null

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
        else -> if (timeline.segments.filterIsInstance<TurnSegment.Activity>()
            .any { group -> group.entries.any { it is TimelineEntry.ToolCall } }
        ) ChatRunStatus.PreparingAnswer else ChatRunStatus.Thinking
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

@Composable
internal fun runStatusLabel(status: ChatRunStatus): String = when (status) {
    ChatRunStatus.Thinking -> stringResource(R.string.chat_process_status_thinking)
    is ChatRunStatus.UsingTool -> stringResource(
        R.string.chat_process_status_tool,
        toolActionTitle(status.toolName),
    )
    ChatRunStatus.PreparingAnswer -> stringResource(R.string.chat_process_status_preparing)
}
