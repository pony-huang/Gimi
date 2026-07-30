package github.ponyhuang.gimi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatDisplayRepository: ChatDisplayRepository,
    appFunctionRepository: AppFunctionRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        chatDisplayRepository.showToolActivity,
        appFunctionRepository.state,
    ) { showToolActivity, appFunctions ->
        SettingsUiState(
            showToolActivity = showToolActivity,
            showAppFunctions =
                appFunctions.support != AppFunctionsSupport.UNSUPPORTED_DEVICE,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(
                showToolActivity = chatDisplayRepository.showToolActivity.value,
                showAppFunctions = appFunctionRepository.state.value.support !=
                    AppFunctionsSupport.UNSUPPORTED_DEVICE,
            ),
        )

    private val mutableEffects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = mutableEffects

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OpenModelService -> emitEffect(SettingsEffect.NavigateToModelService)
            SettingsAction.OpenDefaultModels -> emitEffect(SettingsEffect.NavigateToDefaultModels)
            SettingsAction.OpenVoiceWake -> emitEffect(SettingsEffect.NavigateToVoiceWake)
            SettingsAction.OpenMcpServers -> emitEffect(SettingsEffect.NavigateToMcpServers)
            SettingsAction.OpenSkills -> emitEffect(SettingsEffect.NavigateToSkills)
            SettingsAction.OpenWorkFiles -> emitEffect(SettingsEffect.NavigateToWorkFiles)
            SettingsAction.OpenPermissions -> emitEffect(SettingsEffect.NavigateToPermissions)
            SettingsAction.OpenToolAuthorization ->
                emitEffect(SettingsEffect.NavigateToToolAuthorization)
            SettingsAction.OpenAppFunctions ->
                emitEffect(SettingsEffect.NavigateToAppFunctions)
            is SettingsAction.SetToolActivityVisible ->
                chatDisplayRepository.setShowToolActivity(action.visible)
        }
    }

    private fun emitEffect(effect: SettingsEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
