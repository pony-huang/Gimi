package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ToolAuthorizationViewModel @Inject constructor(
    private val repository: ToolAuthorizationRepository,
    private val setToolAuthorization: SetToolAuthorizationUseCase,
) : ViewModel() {
    private val _effects = MutableSharedFlow<ToolAuthorizationEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ToolAuthorizationEffect> = _effects.asSharedFlow()

    val uiState = combine(
        repository.isCustomizationEnabled,
        repository.tools,
        setToolAuthorization.agentRuntimeState,
    ) { customizationEnabled, tools, runtimeState ->
        ToolAuthorizationUiState(
            isCustomizationEnabled = customizationEnabled,
            tools = tools,
            isMutationBlocked = runtimeState.isBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolAuthorizationUiState(
            isCustomizationEnabled = repository.isCustomizationEnabled.value,
            tools = repository.tools.value,
        ),
    )

    fun onAction(action: ToolAuthorizationAction) {
        when (action) {
            is ToolAuthorizationAction.SetCustomizationEnabled -> viewModelScope.launch {
                val result = setToolAuthorization.setCustomizationEnabled(action.enabled)
                if (result == ToolAuthorizationMutationResult.BlockedByActiveAgent) {
                    _effects.emit(ToolAuthorizationEffect.ShowMessage(ToolAuthorizationMessage.AgentBusy))
                }
            }
        }
    }
}
