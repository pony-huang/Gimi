package github.ponyhuang.gimi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mobileUseRepository: MobileUseRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SettingsUiState(mobileUseEnabled = mobileUseRepository.enabled.value),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            mobileUseRepository.enabled.collect { enabled ->
                mutableUiState.update { it.copy(mobileUseEnabled = enabled) }
            }
        }
    }

    private val mutableEffects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = mutableEffects

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OpenLocalModels -> emitEffect(SettingsEffect.NavigateToLocalModels)
            SettingsAction.OpenModelService -> emitEffect(SettingsEffect.NavigateToModelService)
            SettingsAction.OpenDefaultModels -> emitEffect(SettingsEffect.NavigateToDefaultModels)
            SettingsAction.OpenMcpServers -> emitEffect(SettingsEffect.NavigateToMcpServers)
            SettingsAction.OpenPlugins -> emitEffect(SettingsEffect.NavigateToPlugins)
            SettingsAction.OpenSkills -> emitEffect(SettingsEffect.NavigateToSkills)
            SettingsAction.OpenWorkFiles -> emitEffect(SettingsEffect.NavigateToWorkFiles)
            SettingsAction.OpenWorkspace -> emitEffect(SettingsEffect.NavigateToWorkspace)
            SettingsAction.OpenPermissions -> emitEffect(SettingsEffect.NavigateToPermissions)
            SettingsAction.OpenToolAuthorization ->
                emitEffect(SettingsEffect.NavigateToToolAuthorization)
            SettingsAction.OpenMobileUse -> {
                if (uiState.value.mobileUseEnabled && !uiState.value.mobileUseUpdating) {
                    emitEffect(SettingsEffect.NavigateToMobileUse)
                }
            }
            is SettingsAction.SetMobileUseEnabled -> setMobileUseEnabled(action.enabled)
            SettingsAction.OpenRecommendations ->
                emitEffect(SettingsEffect.NavigateToRecommendations)
            SettingsAction.OpenMemory -> emitEffect(SettingsEffect.NavigateToMemory)
            SettingsAction.OpenAbout -> emitEffect(SettingsEffect.NavigateToAbout)
        }
    }

    private fun setMobileUseEnabled(enabled: Boolean) {
        if (uiState.value.mobileUseUpdating) return
        mutableUiState.update { it.copy(mobileUseUpdating = true) }
        viewModelScope.launch {
            try {
                mobileUseRepository.setEnabled(enabled)
            } finally {
                mutableUiState.update {
                    it.copy(mobileUseEnabled = mobileUseRepository.enabled.value, mobileUseUpdating = false)
                }
            }
        }
    }

    private fun emitEffect(effect: SettingsEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
