package github.ponyhuang.asssistantai.feature.toolauthorization

import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

enum class ToolAuthorizationMutationResult {
    Applied,
    BlockedByActiveAgent,
}

/**
 * Gates tool authorization mutations on the agent runtime: mutations are only
 * applied while the agent is idle, otherwise they are rejected as busy.
 */
class SetToolAuthorizationUseCase @Inject constructor(
    private val repository: ToolAuthorizationRepository,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) {
    val agentRuntimeState: StateFlow<AgentRuntimeState> = runWhenAgentIdle.state

    suspend fun setCustomizationEnabled(enabled: Boolean): ToolAuthorizationMutationResult =
        mutate { repository.setCustomizationEnabled(enabled) }

    suspend fun setToolEnabled(toolId: String, enabled: Boolean): ToolAuthorizationMutationResult =
        mutate { repository.setEnabled(toolId, enabled) }

    private suspend fun mutate(block: () -> Unit): ToolAuthorizationMutationResult =
        when (runWhenAgentIdle { block() }) {
            is AgentMutationResult.Applied -> ToolAuthorizationMutationResult.Applied
            AgentMutationResult.BlockedByActiveAgent -> ToolAuthorizationMutationResult.BlockedByActiveAgent
        }
}
