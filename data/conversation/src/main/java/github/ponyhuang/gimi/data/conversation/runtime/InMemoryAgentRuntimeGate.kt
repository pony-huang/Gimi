package github.ponyhuang.gimi.data.conversation.runtime

import github.ponyhuang.gimi.domain.conversation.runtime.ActiveAgentTask
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class InMemoryAgentRuntimeGate @Inject constructor() : AgentRuntimeGate {
    private val mutationMutex = Mutex()
    private val tasks = linkedMapOf<Any, ActiveAgentTask>()
    private val _state = MutableStateFlow<AgentRuntimeState>(AgentRuntimeState.Idle)

    override val state: StateFlow<AgentRuntimeState> = _state.asStateFlow()

    override suspend fun acquire(
        source: AgentTaskSource,
        sessionId: String?,
        phase: AgentTaskPhase,
    ): AgentRunLease = mutationMutex.withLock {
        synchronized(tasks) {
            val token = Any()
            tasks[token] = ActiveAgentTask(
                source = source,
                sessionId = sessionId,
                phase = phase,
            )
            publishState()
            Lease(token)
        }
    }

    override suspend fun <T> runMutation(
        block: suspend () -> T,
    ): AgentMutationResult<T> = mutationMutex.withLock {
        if (synchronized(tasks) { tasks.isNotEmpty() }) {
            return@withLock AgentMutationResult.BlockedByActiveAgent
        }
        AgentMutationResult.Applied(block())
    }

    private fun publishState() {
        _state.value = if (tasks.isEmpty()) {
            AgentRuntimeState.Idle
        } else {
            AgentRuntimeState.Busy(tasks.values.toList())
        }
    }

    private inner class Lease(
        private val token: Any,
    ) : AgentRunLease {
        override fun updatePhase(phase: AgentTaskPhase) {
            synchronized(tasks) {
                val current = tasks[token] ?: return@synchronized
                if (current.phase == phase) return@synchronized
                tasks[token] = current.copy(phase = phase)
                publishState()
            }
        }

        override fun release() {
            synchronized(tasks) {
                if (tasks.remove(token) != null) publishState()
            }
        }
    }
}
