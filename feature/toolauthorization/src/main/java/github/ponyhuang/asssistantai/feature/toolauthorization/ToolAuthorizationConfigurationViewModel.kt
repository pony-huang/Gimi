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
class ToolAuthorizationConfigurationViewModel @Inject constructor(
    private val repository: ToolAuthorizationRepository,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ToolAuthorizationFilter.ALL)
    private val notice = MutableStateFlow<String?>(null)

    val uiState = combine(
        repository.tools,
        query,
        filter,
        runWhenAgentIdle.state,
        notice,
    ) { tools, currentQuery, currentFilter, runtimeState, currentNotice ->
        ToolAuthorizationConfigurationUiState(
            query = currentQuery,
            filter = currentFilter,
            tools = tools,
            isMutationBlocked = runtimeState.isBusy,
            notice = currentNotice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolAuthorizationConfigurationUiState(
            tools = repository.tools.value,
        ),
    )

    fun onAction(action: ToolAuthorizationConfigurationAction) {
        when (action) {
            is ToolAuthorizationConfigurationAction.Search -> query.value = action.query
            is ToolAuthorizationConfigurationAction.SetFilter -> filter.value = action.filter
            is ToolAuthorizationConfigurationAction.SetEnabled -> mutate {
                repository.setEnabled(action.toolId, action.enabled)
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
