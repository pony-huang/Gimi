package github.ponyhuang.gimi.domain.conversation.runtime

import kotlinx.coroutines.flow.StateFlow

enum class AgentTaskSource {
    CHAT,
    BLUETOOTH_VOICE,
    SYSTEM_ASSISTANT,
}

enum class AgentTaskPhase {
    GENERATING,
    EXECUTING_TOOL,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_INPUT,
}

data class ActiveAgentTask(
    val source: AgentTaskSource,
    val sessionId: String? = null,
    val phase: AgentTaskPhase,
)

sealed interface AgentRuntimeState {
    data object Idle : AgentRuntimeState

    data class Busy(
        val tasks: List<ActiveAgentTask>,
    ) : AgentRuntimeState
}

val AgentRuntimeState.isBusy: Boolean
    get() = this is AgentRuntimeState.Busy

sealed interface AgentMutationResult<out T> {
    data class Applied<T>(val value: T) : AgentMutationResult<T>
    data object BlockedByActiveAgent : AgentMutationResult<Nothing>
}

interface AgentRunLease {
    fun updatePhase(phase: AgentTaskPhase)
    fun release()
}

/** 同一会话已有活动任务，新的写入任务不能启动。 */
class AgentSessionBusyException(sessionId: String) :
    IllegalStateException("Conversation $sessionId already has an active agent task.")

interface AgentRuntimeGate {
    val state: StateFlow<AgentRuntimeState>

    suspend fun acquire(
        source: AgentTaskSource,
        sessionId: String? = null,
        phase: AgentTaskPhase = AgentTaskPhase.GENERATING,
    ): AgentRunLease

    suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T>
}
