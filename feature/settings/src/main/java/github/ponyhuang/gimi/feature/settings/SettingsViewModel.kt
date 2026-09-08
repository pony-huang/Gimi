package github.ponyhuang.gimi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = MutableStateFlow(SettingsUiState())

    private val mutableEffects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = mutableEffects

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OpenModelService -> emitEffect(SettingsEffect.NavigateToModelService)
            SettingsAction.OpenDefaultModels -> emitEffect(SettingsEffect.NavigateToDefaultModels)
            SettingsAction.OpenVoiceWake -> emitEffect(SettingsEffect.NavigateToVoiceWake)
            SettingsAction.OpenMcpServers -> emitEffect(SettingsEffect.NavigateToMcpServers)
            SettingsAction.OpenPlugins -> emitEffect(SettingsEffect.NavigateToPlugins)
            SettingsAction.OpenSkills -> emitEffect(SettingsEffect.NavigateToSkills)
            SettingsAction.OpenWorkFiles -> emitEffect(SettingsEffect.NavigateToWorkFiles)
            SettingsAction.OpenPermissions -> emitEffect(SettingsEffect.NavigateToPermissions)
            SettingsAction.OpenToolAuthorization ->
                emitEffect(SettingsEffect.NavigateToToolAuthorization)
            SettingsAction.OpenRecommendations ->
                emitEffect(SettingsEffect.NavigateToRecommendations)
            SettingsAction.OpenMemory -> emitEffect(SettingsEffect.NavigateToMemory)
            SettingsAction.OpenProjectPage ->
                emitEffect(SettingsEffect.OpenProjectPage)
        }
    }

    private fun emitEffect(effect: SettingsEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
