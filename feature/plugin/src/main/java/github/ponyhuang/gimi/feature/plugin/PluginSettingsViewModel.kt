package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PluginSettingsViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    val uiState: StateFlow<PluginSettingsUiState> = repository.plugins
        .map(::PluginSettingsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PluginSettingsUiState(repository.plugins.value),
        )

    private val mutableEffects = MutableSharedFlow<PluginSettingsEffect>()
    val effects: SharedFlow<PluginSettingsEffect> = mutableEffects.asSharedFlow()

    fun onAction(action: PluginSettingsAction) {
        when (action) {
            is PluginSettingsAction.SetEnabled ->
                repository.setEnabled(action.pluginId, action.enabled)
            PluginSettingsAction.Refresh -> refresh()
        }
    }

    /** 重新发现插件；有新增时发通知。 */
    private fun refresh() {
        viewModelScope.launch {
            val added = repository.refresh()
            if (added.isNotEmpty()) {
                mutableEffects.emit(PluginSettingsEffect.ShowPluginAdded(added))
            }
        }
    }
}
