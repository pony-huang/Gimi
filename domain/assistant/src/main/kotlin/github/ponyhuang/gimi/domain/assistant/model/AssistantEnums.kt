package github.ponyhuang.gimi.domain.assistant.model

/** 助理会话的阶段。浮层与蓝牙链路共用同一组阶段描述当前语音会话进度。 */
enum class AssistantSessionPhase {
    /** 唤起后正在准备（恢复状态或等待首次交互）。 */
    PREPARING,

    /** 缺少可用助理模型，无法执行任务。 */
    MISSING_CONFIG,

    /** 当前聊天会话已有任务，无法并发写入。 */
    BUSY,

    /** 正在录音监听用户语音。 */
    LISTENING,

    /** 录音结束，正在转写。 */
    TRANSCRIBING,

    /** Agent 正在生成回答。 */
    GENERATING,

    /** Agent 正在执行工具。 */
    EXECUTING_TOOL,

    /** 等待用户确认敏感工具调用。 */
    AWAITING_CONFIRMATION,

    /** Agent 在需用户输入的工具上挂起（`get_user_choice` / `adk_request_input`），等待答复。 */
    AWAITING_INPUT,

    /** 正在播报回答。 */
    SPEAKING,

    /** 回答完成，可手动追问；空闲一段时间后浮层自动关闭。 */
    FOLLOW_UP_IDLE,

    /** 用户主动停止了当前任务。 */
    STOPPED,

    /** 发生错误，可重试。 */
    ERROR,
}

/** 唤起助理的入口来源。 */
enum class AssistantInvocationSource {
    /** 蓝牙唤醒词。 */
    BLUETOOTH_WAKE,

    /** 助手面板内直接发起的追问（语音或键盘输入）。 */
    ASSISTANT_PANEL,
}

/** 结果完成后可自动收起的阶段；其它阶段应保持展示等待用户交互。 */
fun AssistantSessionPhase.isPresentationResultIdle(): Boolean = when (this) {
    AssistantSessionPhase.FOLLOW_UP_IDLE,
    AssistantSessionPhase.STOPPED,
    AssistantSessionPhase.ERROR,
    AssistantSessionPhase.AWAITING_INPUT,
    -> true
    else -> false
}
