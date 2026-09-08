package github.ponyhuang.gimi.feature.toolauthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.toolauthorization.usecase.SetToolAuthorizationUseCase
import github.ponyhuang.gimi.domain.toolauthorization.usecase.ToolAuthorizationMutationResult
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
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
    private val toolAccessRepository: ToolAccessRepository,
    private val chatDisplayRepository: ChatDisplayRepository,
) : ViewModel() {
    private val _effects = MutableSharedFlow<ToolAuthorizationEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ToolAuthorizationEffect> = _effects.asSharedFlow()

    val uiState = combine(
        repository.isCustomizationEnabled,
        repository.tools,
        setToolAuthorization.agentRuntimeState,
        toolAccessRepository.defaultToolAccessMode,
        chatDisplayRepository.showToolActivity,
    ) { customizationEnabled, tools, runtimeState, toolAccessMode, showToolActivity ->
        ToolAuthorizationUiState(
            isCustomizationEnabled = customizationEnabled,
            tools = tools,
            isMutationBlocked = runtimeState.isBusy,
            toolAccessMode = toolAccessMode,
            showToolActivity = showToolActivity,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolAuthorizationUiState(
            isCustomizationEnabled = repository.isCustomizationEnabled.value,
            tools = repository.tools.value,
            toolAccessMode = toolAccessRepository.defaultToolAccessMode.value,
            showToolActivity = chatDisplayRepository.showToolActivity.value,
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
            is ToolAuthorizationAction.SetToolAccessMode ->
                toolAccessRepository.setDefaultToolAccessMode(action.mode)
            is ToolAuthorizationAction.SetShowToolActivity ->
                chatDisplayRepository.setShowToolActivity(action.visible)
        }
    }
}
