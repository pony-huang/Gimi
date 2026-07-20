package github.ponyhuang.asssistantai.feature.modelsettings

import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestAgentRuntimeGate : AgentRuntimeGate {
    override val state = MutableStateFlow<AgentRuntimeState>(AgentRuntimeState.Idle)

    override suspend fun acquire(source: AgentTaskSource, phase: AgentTaskPhase): AgentRunLease =
        object : AgentRunLease {
            override fun updatePhase(phase: AgentTaskPhase) = Unit
            override fun release() = Unit
        }

    override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
        AgentMutationResult.Applied(block())
}
