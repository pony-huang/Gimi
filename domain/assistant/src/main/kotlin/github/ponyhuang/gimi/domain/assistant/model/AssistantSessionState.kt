package github.ponyhuang.gimi.domain.assistant.model

/** 助手会话气泡的作者。 */
enum class AssistantMessageAuthor {
    /** 用户输入的消息。 */
    USER,

    /** 助手回复的消息。 */
    ASSISTANT,
}

/**
 * 助手会话中的一个对话气泡。
 *
 * @property id 会话内单调递增标识，用于稳定渲染 key。
 * @property author 气泡作者。
 * @property text 气泡文本。
 * @property streaming 是否仍处于流式生成（用于显示等待指示或增量渲染）。
 * @property isError 是否为错误提示气泡。
 * @property toolNames 该轮已调用/正在调用的工具名（去重、按出现顺序）。
 */
data class AssistantMessage(
    val id: Long,
    val author: AssistantMessageAuthor,
    val text: String = "",
    val streaming: Boolean = false,
    val isError: Boolean = false,
    val toolNames: List<String> = emptyList(),
)

/** 等待用户确认的敏感工具调用。 */
data class PendingAssistantConfirmation(
    val confirmationCallId: String,
    val toolName: String,
    val arguments: Map<String, Any?> = emptyMap(),
    /** 确认截止时间（epoch 毫秒）；超时自动拒绝。 */
    val deadlineEpochMs: Long,
)

/** 助理会话的当前一轮问答。新唤起默认只展示当前轮。 */
data class AssistantTurn(
    val userText: String = "",
    val responseText: String = "",
    /** 本轮已调用/正在调用的工具名（去重、按出现顺序）。 */
    val toolNames: List<String> = emptyList(),
)

/** 助理会话的可观察状态。 */
data class AssistantSessionState(
    /** 当前任务绑定的聊天会话 id；尚未提交任务时为 null。 */
    val sessionId: String? = null,
    val phase: AssistantSessionPhase = AssistantSessionPhase.PREPARING,
    /** 最近一次唤起来源。 */
    val source: AssistantInvocationSource? = null,
    /** 当前轮问答；无活动轮次时为 null。 */
    val turn: AssistantTurn? = null,
    val pendingConfirmation: PendingAssistantConfirmation? = null,
    val errorMessage: String? = null,
    val configIssue: AssistantConfigIssue? = null,
    /** 是否有已提交且仍在执行的任务（关闭浮层不取消）。 */
    val taskActive: Boolean = false,
    /** 当前语音交互是否需要展示；具体承载界面由运行环境决定。 */
    val presentationVisible: Boolean = false,
    /** 本次助手会话（自最后一次唤起起）的有序气泡列表。 */
    val messages: List<AssistantMessage> = emptyList(),
) {
    /** 是否处于可恢复展示的活动中状态（再次唤起时不重新录音）。 */
    val hasActiveTask: Boolean
        get() = taskActive
}

/**
 * 是否应展开会话面板：存在轮次、消息或待确认工具时展开为对话面板，
 * 否则保持悬浮胶囊态（刚唤醒、仅采集指令的阶段）。
 */
val AssistantSessionState.shouldShowConversation: Boolean
    get() = turn != null || messages.isNotEmpty() || pendingConfirmation != null

/** 追加一条用户输入气泡。 */
fun AssistantSessionState.appendUserMessage(text: String): AssistantSessionState {
    val nextId = nextMessageId(messages)
    return copy(messages = messages + AssistantMessage(nextId, AssistantMessageAuthor.USER, text))
}

/** 追加一条助手气泡，默认处于流式生成状态。 */
fun AssistantSessionState.appendAssistantMessage(
    text: String = "",
    streaming: Boolean = true,
    toolNames: List<String> = emptyList(),
): AssistantSessionState {
    val nextId = nextMessageId(messages)
    return copy(
        messages = messages + AssistantMessage(
            id = nextId,
            author = AssistantMessageAuthor.ASSISTANT,
            text = text,
            streaming = streaming,
            toolNames = toolNames,
        ),
    )
}

/** 原地更新最后一条助手气泡的流式文本（仅当存在助手气泡时）。 */
fun AssistantSessionState.updateLastAssistantMessage(
    text: String? = null,
    streaming: Boolean = false,
    toolNames: List<String>? = null,
): AssistantSessionState {
    val lastAssistantIndex = messages.indexOfLast { it.author == AssistantMessageAuthor.ASSISTANT }
    if (lastAssistantIndex < 0) return this
    val current = messages[lastAssistantIndex]
    val updated = current.copy(
        text = text ?: current.text,
        streaming = streaming,
        isError = false,
        toolNames = toolNames ?: current.toolNames,
    )
    return copy(messages = messages.updated(lastAssistantIndex, updated))
}

/** 终止最后一条助手气泡为流式错误。 */
fun AssistantSessionState.failLastAssistantMessage(message: String): AssistantSessionState {
    val lastAssistantIndex = messages.indexOfLast { it.author == AssistantMessageAuthor.ASSISTANT }
    if (lastAssistantIndex < 0) return copy()
    val current = messages[lastAssistantIndex]
    val updated = current.copy(text = message, streaming = false, isError = true)
    return copy(messages = messages.updated(lastAssistantIndex, updated))
}

private fun nextMessageId(messages: List<AssistantMessage>): Long =
    (messages.maxOfOrNull { it.id } ?: 0L) + 1L

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    toMutableList().apply { this[index] = value }
