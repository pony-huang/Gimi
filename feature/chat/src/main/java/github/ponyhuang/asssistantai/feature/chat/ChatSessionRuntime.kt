package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.Message
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
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
    var isLoaded: Boolean = false
    var isAgentRunning: Boolean = false
    var turnComplete: Boolean = false
    var phase: AgentTaskPhase = AgentTaskPhase.GENERATING
    var pendingToolConfirmations: List<PendingToolConfirmation> = emptyList()
    val approvedToolsThisTurn: MutableSet<String> = mutableSetOf()
    var job: Job? = null
    var lease: AgentRunLease? = null
    var runToken: Any? = null
    var failed: Boolean = false
    var attention: SessionResultAttention = SessionResultAttention.NONE

    private val partChannels = mutableMapOf<String, Channel<String>>()

    val isActive: Boolean
        get() = job?.isActive == true || isAgentRunning || pendingToolConfirmations.isNotEmpty()

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
        partChannels.getOrPut(partId) { Channel(Channel.UNLIMITED) }.trySend(delta)
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
