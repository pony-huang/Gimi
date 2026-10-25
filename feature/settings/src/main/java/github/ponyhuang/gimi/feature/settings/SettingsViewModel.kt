package github.ponyhuang.gimi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** 设置首页只负责分发导航意图；页面内开关由各功能页自己持有状态。 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val mutableEffects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = mutableEffects

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OpenModelService -> emitEffect(SettingsEffect.NavigateToModelService)
            SettingsAction.OpenDefaultModels -> emitEffect(SettingsEffect.NavigateToDefaultModels)
            SettingsAction.OpenMcpServers -> emitEffect(SettingsEffect.NavigateToMcpServers)
            SettingsAction.OpenRemoteAgents -> emitEffect(SettingsEffect.NavigateToRemoteAgents)
            SettingsAction.OpenAppFunctions -> emitEffect(SettingsEffect.NavigateToAppFunctions)
            SettingsAction.OpenSandbox -> emitEffect(SettingsEffect.NavigateToSandbox)
            SettingsAction.OpenPlugins -> emitEffect(SettingsEffect.NavigateToPlugins)
            SettingsAction.OpenSkills -> emitEffect(SettingsEffect.NavigateToSkills)
            SettingsAction.OpenWorkspace -> emitEffect(SettingsEffect.NavigateToWorkspace)
            SettingsAction.OpenPermissions -> emitEffect(SettingsEffect.NavigateToPermissions)
            SettingsAction.OpenMobileUse -> emitEffect(SettingsEffect.NavigateToMobileUse)
            SettingsAction.OpenRecommendations ->
                emitEffect(SettingsEffect.NavigateToRecommendations)
            SettingsAction.OpenMemory -> emitEffect(SettingsEffect.NavigateToMemory)
            SettingsAction.OpenAbout -> emitEffect(SettingsEffect.NavigateToAbout)
        }
    }

    private fun emitEffect(effect: SettingsEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
