package github.ponyhuang.asssistantai.domain.assistant.model

/** 助理会话的阶段。浮层与蓝牙链路共用同一组阶段描述当前语音会话进度。 */
enum class AssistantSessionPhase {
    /** 唤起后正在准备（恢复状态或等待首次交互）。 */
    PREPARING,

    /** 缺少必要配置（默认助理模型或 STT），无法执行任务。 */
    MISSING_CONFIG,

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

    /** 正在播报回答。 */
    SPEAKING,

    /** 回答完成，可手动追问；空闲一段时间后浮层自动关闭。 */
    FOLLOW_UP_IDLE,

    /** 发生错误，可重试。 */
    ERROR,
}

/** 唤起助理的入口来源。 */
enum class AssistantInvocationSource {
    /** 蓝牙唤醒词。 */
    BLUETOOTH_WAKE,
}

/** 缺少的配置项。 */
enum class AssistantConfigIssue {
    MISSING_AGENT_MODEL,
    MISSING_STT,
}