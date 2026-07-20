package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ToolAuthorizationViewModel @Inject constructor(
    private val repository: ToolAuthorizationRepository,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)

    val uiState = combine(repository.tools, query, runWhenAgentIdle.state, notice) {
            tools, currentQuery, runtimeState, currentNotice ->
        ToolAuthorizationUiState(
            query = currentQuery,
            tools = tools,
            isMutationBlocked = runtimeState.isBusy,
            notice = currentNotice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolAuthorizationUiState(tools = repository.tools.value),
    )

    fun onAction(action: ToolAuthorizationAction) {
        when (action) {
            is ToolAuthorizationAction.Search -> query.value = action.query
            is ToolAuthorizationAction.SetEnabled -> mutate {
                repository.setEnabled(action.toolId, action.enabled)
            }
            is ToolAuthorizationAction.SetAllEnabled -> mutate {
                repository.setAllEnabled(action.enabled)
            }
        }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            notice.value = when (runWhenAgentIdle { block() }) {
                is AgentMutationResult.Applied -> null
                AgentMutationResult.BlockedByActiveAgent -> BLOCKED_MESSAGE
            }
        }
    }

    private companion object {
        const val BLOCKED_MESSAGE = ""
    }
}
