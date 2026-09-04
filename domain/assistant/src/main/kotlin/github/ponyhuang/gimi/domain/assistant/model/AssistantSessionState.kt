package github.ponyhuang.gimi.domain.assistant.model

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
) {
    /** 是否处于可恢复展示的活动中状态（再次唤起时不重新录音）。 */
    val hasActiveTask: Boolean
        get() = taskActive
}
