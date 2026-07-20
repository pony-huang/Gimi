package github.ponyhuang.asssistantai.domain.conversation.usecase

import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class RunWhenAgentIdleUseCase @Inject constructor(
    private val gate: AgentRuntimeGate,
) {
    val state: StateFlow<AgentRuntimeState> = gate.state

    suspend operator fun <T> invoke(block: suspend () -> T): AgentMutationResult<T> =
        gate.runMutation(block)
}
