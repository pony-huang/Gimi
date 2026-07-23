package github.ponyhuang.asssistantai.feature.assistant

import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.asssistantai.domain.assistant.model.PendingAssistantConfirmation

/** 助理浮层的完整界面状态。 */
data class AssistantOverlayUiState(
    val phase: AssistantSessionPhase = AssistantSessionPhase.PREPARING,
    val source: AssistantInvocationSource? = null,
    /** 当前轮用户指令（语音转写或键盘输入）。 */
    val userText: String = "",
    /** 当前轮回答；始终展示。 */
    val responseText: String = "",
    val toolNames: List<String> = emptyList(),
    val pendingConfirmation: PendingAssistantConfirmation? = null,
    val confirmationRemainingSeconds: Int = 0,
    val configIssue: AssistantConfigIssue? = null,
    val errorMessage: String? = null,
    /** 录音波形电平（0..1）。 */
    val recordingLevels: List<Float> = emptyList(),
    val draftText: String = "",
    val isSpeaking: Boolean = false,
    /** TTS 未配置或播放失败时的非阻塞提示。 */
    val ttsNotice: Boolean = false,
    /** 起声超时/转写为空/录音失败后可重试。 */
    val canRetryListening: Boolean = false,
    val voiceSessionId: String? = null,
) {
    val inputEnabled: Boolean
        get() = phase == AssistantSessionPhase.FOLLOW_UP_IDLE ||
            phase == AssistantSessionPhase.ERROR ||
            canRetryListening
}

sealed interface AssistantOverlayAction {
    /** 手动追问：开始录音。 */
    data object MicTapped : AssistantOverlayAction

    /** 取消当前录音。 */
    data object StopListening : AssistantOverlayAction

    data class DraftChanged(val value: String) : AssistantOverlayAction

    /** 提交键盘输入。 */
    data object SubmitDraft : AssistantOverlayAction

    /** 停止当前任务（唯一取消任务的途径）。 */
    data object StopTask : AssistantOverlayAction

    /** 关闭浮层：不取消已提交任务。 */
    data object CloseOverlay : AssistantOverlayAction

    data object ApproveConfirmation : AssistantOverlayAction
    data object RejectConfirmation : AssistantOverlayAction
    data object StopSpeaking : AssistantOverlayAction
    data object RetryAfterError : AssistantOverlayAction

    /** 滚动等任意交互：重置空闲关闭计时。 */
    data object UserActivity : AssistantOverlayAction

    /** 麦克风运行时权限申请结果。 */
    data class MicPermissionResult(val granted: Boolean) : AssistantOverlayAction
}

/** 一次性界面事件。 */
sealed interface AssistantOverlayEvent {
    /** 请求关闭浮层（空闲超时或用户关闭）。 */
    data object CloseOverlay : AssistantOverlayEvent

    /** 请求麦克风运行时权限。 */
    data object RequestMicrophonePermission : AssistantOverlayEvent
}
