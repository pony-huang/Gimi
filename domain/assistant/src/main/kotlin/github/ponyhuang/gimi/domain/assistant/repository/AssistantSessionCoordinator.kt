package github.ponyhuang.gimi.domain.assistant.repository

import github.ponyhuang.gimi.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.PendingAssistantConfirmation
import kotlinx.coroutines.flow.StateFlow

/** 敏感工具确认的处理端。浮层实现展示倒计时确认卡片；蓝牙实现走 TTS 询问 + 语音判定。 */
fun interface AssistantConfirmationHandler {
    suspend fun confirm(request: PendingAssistantConfirmation): Boolean
}

/**
 * 进程级助理会话协调器：统一系统浮层与蓝牙语音任务，并把请求提交到当前聊天会话。
 *
 * 关闭展示界面（[hidePresentation]）不取消已提交任务；只有 [stop] 取消任务。
 */
interface AssistantSessionCoordinator {
    /** 单一可观察状态源；浮层重建时从此恢复。 */
    val state: StateFlow<AssistantSessionState>

    /** 检查执行所需配置；返回 null 表示可执行。 */
    suspend fun configurationIssue(): AssistantConfigIssue?

    /** 记录一次唤起：恢复浮层可见性，不启动新任务、不重置进行中的任务。 */
    fun noteInvocation(source: AssistantInvocationSource)

    /** 将录音、转写和播报阶段投射到共享助手界面。 */
    fun updatePresentation(event: AssistantPresentationEvent)

    /**
     * 提交一条用户指令到当前聊天会话并挂起直到任务结束（完成/失败/被取消）。
     * 助手入口的请求按提交顺序串行执行。
     *
     * 等待方协程被取消（如浮层销毁）不会取消任务本身；取消任务必须调用 [stop]。
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

    /**
     * 答复等待中的敏感工具确认。
     *
     * 只有 [confirmationCallId] 与当前等待项一致时才接受，避免解锁等异步旧回调
     * 误批准后续工具调用。
     *
     * @return true 表示答复已交付给当前等待项。
     */
    fun respondToConfirmation(confirmationCallId: String, confirmed: Boolean): Boolean

    /** 仅隐藏展示界面：停止界面层交互，不取消已提交任务。 */
    fun hidePresentation()
}
