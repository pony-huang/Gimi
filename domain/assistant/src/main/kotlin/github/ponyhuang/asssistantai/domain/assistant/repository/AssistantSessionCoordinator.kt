package github.ponyhuang.asssistantai.domain.assistant.repository

import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionState
import github.ponyhuang.asssistantai.domain.assistant.model.PendingAssistantConfirmation
import kotlinx.coroutines.flow.StateFlow

/** 敏感工具确认的处理端。浮层实现展示倒计时确认卡片；蓝牙实现走 TTS 询问 + 语音判定。 */
fun interface AssistantConfirmationHandler {
    suspend fun confirm(request: PendingAssistantConfirmation): Boolean
}

/**
 * 进程级助理会话协调器：统一系统浮层与蓝牙语音任务，串行化同一语音会话的请求。
 *
 * 关闭浮层（[hideOverlay]）不取消已提交任务；只有 [stop] 取消任务。
 */
interface AssistantSessionCoordinator {
    /** 单一可观察状态源；浮层重建时从此恢复。 */
    val state: StateFlow<AssistantSessionState>

    /** 共享语音会话 id（不创建新会话；未创建时为 null）。 */
    val voiceSessionId: StateFlow<String?>

    /** 检查执行所需配置；返回 null 表示可执行。 */
    suspend fun configurationIssue(): AssistantConfigIssue?

    /** 记录一次唤起：恢复浮层可见性，不启动新任务、不重置进行中的任务。 */
    fun noteInvocation(source: AssistantInvocationSource)

    /**
     * 提交一条用户指令到共享语音会话并挂起直到任务结束（完成/失败/被取消）。
     * 同一语音会话的请求按提交顺序串行执行。
     *
     * @param confirmationHandler 敏感工具确认通道；为 null 时通过状态流暴露确认请求，
     * 由调用方使用 [respondToConfirmation] 答复（15 秒超时自动拒绝）。
     */
    suspend fun submit(
        text: String,
        source: AssistantInvocationSource,
        confirmationHandler: AssistantConfirmationHandler? = null,
    )

    /** 取消当前任务并释放运行时租约。 */
    fun stop()

    /** 答复等待中的敏感工具确认。 */
    fun respondToConfirmation(confirmed: Boolean)

    /** 仅隐藏浮层：停止界面层交互，不取消已提交任务。 */
    fun hideOverlay()
}
