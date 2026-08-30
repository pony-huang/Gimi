package github.ponyhuang.gimi.feature.toolauthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.toolauthorization.usecase.SetToolAuthorizationUseCase
import github.ponyhuang.gimi.domain.toolauthorization.usecase.ToolAuthorizationMutationResult
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ToolAuthorizationConfigurationViewModel @Inject constructor(
    private val repository: ToolAuthorizationRepository,
    private val setToolAuthorization: SetToolAuthorizationUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ToolAuthorizationFilter.ALL)

    private val _effects = MutableSharedFlow<ToolAuthorizationEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ToolAuthorizationEffect> = _effects.asSharedFlow()

    val uiState = combine(
        repository.tools,
        query,
        filter,
        setToolAuthorization.agentRuntimeState,
    ) { tools, currentQuery, currentFilter, runtimeState ->
        ToolAuthorizationConfigurationUiState(
            query = currentQuery,
            filter = currentFilter,
            tools = tools,
            isMutationBlocked = runtimeState.isBusy,
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
            is ToolAuthorizationConfigurationAction.SetEnabled -> viewModelScope.launch {
                val result = setToolAuthorization.setToolEnabled(action.toolId, action.enabled)
                if (result == ToolAuthorizationMutationResult.BlockedByActiveAgent) {
                    _effects.emit(ToolAuthorizationEffect.ShowMessage(ToolAuthorizationMessage.AgentBusy))
                }
            }
        }
    }
}
