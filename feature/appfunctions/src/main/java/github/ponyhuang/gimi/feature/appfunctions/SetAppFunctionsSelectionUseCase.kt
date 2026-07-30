package github.ponyhuang.gimi.feature.appfunctions

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** AppFunctions 授权变更结果。 */
enum class AppFunctionsMutationResult {
    APPLIED,
    BLOCKED_BY_ACTIVE_AGENT,
    UNAVAILABLE,
}

/**
 * 仅在 Agent 空闲时更新 AppFunctions 目录选择，防止确认恢复时工具声明漂移。
 */
class SetAppFunctionsSelectionUseCase @Inject constructor(
    private val repository: AppFunctionRepository,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) {
    val agentRuntimeState: StateFlow<AgentRuntimeState> = runWhenAgentIdle.state

    suspend fun setFeatureEnabled(enabled: Boolean): AppFunctionsMutationResult =
        when (val result = runWhenAgentIdle { repository.setFeatureEnabled(enabled) }) {
            is AgentMutationResult.Applied -> {
                if (result.value) AppFunctionsMutationResult.APPLIED
                else AppFunctionsMutationResult.UNAVAILABLE
            }
            AgentMutationResult.BlockedByActiveAgent ->
                AppFunctionsMutationResult.BLOCKED_BY_ACTIVE_AGENT
        }

    suspend fun setAppEnabled(
        packageName: String,
        enabled: Boolean,
    ): AppFunctionsMutationResult = mutate {
        repository.setAppEnabled(packageName, enabled)
    }

    suspend fun setFunctionEnabled(
        key: AppFunctionKey,
        enabled: Boolean,
    ): AppFunctionsMutationResult = mutate {
        repository.setFunctionEnabled(key, enabled)
    }

    private suspend fun mutate(block: suspend () -> Unit): AppFunctionsMutationResult =
        when (runWhenAgentIdle(block)) {
            is AgentMutationResult.Applied -> AppFunctionsMutationResult.APPLIED
            AgentMutationResult.BlockedByActiveAgent ->
                AppFunctionsMutationResult.BLOCKED_BY_ACTIVE_AGENT
        }
}
