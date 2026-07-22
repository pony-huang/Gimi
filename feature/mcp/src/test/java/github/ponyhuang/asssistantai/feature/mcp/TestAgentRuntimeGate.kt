package github.ponyhuang.asssistantai.feature.mcp

import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestAgentRuntimeGate : AgentRuntimeGate {
    override val state = MutableStateFlow<AgentRuntimeState>(AgentRuntimeState.Idle)
    override suspend fun acquire(
        source: AgentTaskSource,
        sessionId: String?,
        phase: AgentTaskPhase,
    ): AgentRunLease =
        error("not used")
    override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
        AgentMutationResult.Applied(block())
}
