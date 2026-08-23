package github.ponyhuang.gimi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatDisplayRepository: ChatDisplayRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = chatDisplayRepository.showToolActivity
        .map(::SettingsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(chatDisplayRepository.showToolActivity.value),
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
            SettingsAction.OpenProjectPage ->
                emitEffect(SettingsEffect.OpenProjectPage)
            is SettingsAction.SetToolActivityVisible ->
                chatDisplayRepository.setShowToolActivity(action.visible)
        }
    }

    private fun emitEffect(effect: SettingsEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
