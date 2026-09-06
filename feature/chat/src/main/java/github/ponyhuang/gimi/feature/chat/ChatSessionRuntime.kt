package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

internal enum class SessionResultAttention {
    NONE,
    COMPLETED,
    FAILED,
}

/** Process-local state owned by exactly one conversation. */
internal class ChatSessionRuntime(
    val sessionId: String,
) {
    var messages: List<Message> = emptyList()
    var modelSelection: ModelSelection? = null
    var toolConfiguration: ConversationToolConfiguration? = null
    var isLoaded: Boolean = false
    var isAgentRunning: Boolean = false
    var turnComplete: Boolean = false
    var phase: AgentTaskPhase = AgentTaskPhase.GENERATING
    var pendingToolConfirmations: List<PendingToolConfirmation> = emptyList()

    /**
     * 已按授权策略（完全批准 / 「总是允许」白名单）放行、等待 run 流结束后自动回复 ADK 的确认。
     * 与 [pendingToolConfirmations] 分开存放：这条通道不进 UI state，不渲染确认卡片。
     */
    var autoApprovedConfirmations: List<PendingToolConfirmation> = emptyList()
    val approvedToolsThisTurn: MutableSet<String> = mutableSetOf()

    /**
     * 本次会话里被用户拒绝确认的工具名（纯内存展示态，不进 Room/session）。
     * 拒绝后 ADK 不会再补发原始工具的 FunctionResponse，chip 只能靠这个集合显示 ✗。
     */
    val rejectedToolNames: MutableSet<String> = mutableSetOf()
    var job: Job? = null
    var lease: AgentRunLease? = null
    var runToken: Any? = null
    var failed: Boolean = false
    var attention: SessionResultAttention = SessionResultAttention.NONE

    /** 最近一次发送尝试（RUNNING/FAILED/INTERRUPTED）；驱动重试与编辑入口。 */
    var lastTurn: ChatTurn? = null

    private val partChannels = mutableMapOf<String, Channel<String>>()

    val isActive: Boolean
        get() = job?.isActive == true ||
            isAgentRunning ||
            pendingToolConfirmations.isNotEmpty() ||
            autoApprovedConfirmations.isNotEmpty()

    fun drawerStatus(): ConversationTaskStatus? = when {
        pendingToolConfirmations.isNotEmpty() -> ConversationTaskStatus.WaitingForConfirmation(
            pendingToolConfirmations.size,
        )
        isActive -> ConversationTaskStatus.Running(phase)
        attention == SessionResultAttention.COMPLETED -> ConversationTaskStatus.Completed
        attention == SessionResultAttention.FAILED -> ConversationTaskStatus.Failed
        else -> null
    }

    fun partChannel(partId: String): ReceiveChannel<String>? = partChannels[partId]

    fun emitPartDelta(partId: String, delta: String) {
        if (delta.isEmpty()) return
        partChannels.getOrPut(partId) { Channel(Channel.BUFFERED) }.trySend(delta)
    }

    fun closePartChannels() {
        partChannels.values.forEach { it.close() }
        partChannels.clear()
    }

    /** Recreates streaming channels after returning to a background-running conversation. */
    fun reseedPartialChannels() {
        closePartChannels()
        messages.asSequence()
            .filter { it.partial }
            .flatMap { it.textParts.asSequence() }
            .forEach { part -> emitPartDelta(part.id, part.text) }
    }
}
