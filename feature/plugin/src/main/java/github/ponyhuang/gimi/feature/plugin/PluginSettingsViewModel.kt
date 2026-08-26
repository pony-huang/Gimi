package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PluginSettingsViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<PluginSettingsUiState> = combine(repository.plugins, isRefreshing) { plugins, refreshing ->
        PluginSettingsUiState(plugins = plugins, isRefreshing = refreshing)
    }.stateIn(
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

    /** 重新发现插件；有新增时发通知。期间置位刷新标记驱动下拉指示器。 */
    private fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                val added = repository.refresh()
                if (added.isNotEmpty()) {
                    mutableEffects.emit(PluginSettingsEffect.ShowPluginAdded(added))
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }
}
