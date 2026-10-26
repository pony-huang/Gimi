package github.ponyhuang.gimi.domain.conversation.runtime

import kotlinx.coroutines.flow.StateFlow

enum class AgentTaskSource {
    CHAT,
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

/** 未绑定会话的活动任务保守视为全局写入；null 范围表示读取全部会话。 */
fun AgentRuntimeState.blocksSessionRead(sessionId: String?): Boolean =
    this is AgentRuntimeState.Busy && tasks.any {
        sessionId == null || it.sessionId == null || it.sessionId == sessionId
    }

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

    /** 检查目标会话空闲并在读取期间阻止新任务进入，避免状态检查与实际读取之间的竞态。 */
    suspend fun <T> readSessionSnapshot(
        sessionId: String?,
        block: suspend () -> T,
    ): AgentMutationResult<T>
}
