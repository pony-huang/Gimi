package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    fun onAction(action: PluginSettingsAction) {
        when (action) {
            is PluginSettingsAction.SetEnabled ->
                repository.setEnabled(action.pluginId, action.enabled)
        }
    }
}
