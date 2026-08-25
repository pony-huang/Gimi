package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class PluginConfigViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginConfigUiState())
    val state: StateFlow<PluginConfigUiState> = _state.asStateFlow()

    private var loadedPluginId: String? = null

    /** 按 pluginId 载入配置描述与已存值；同一插件幂等，避免重复加载覆盖编辑中的状态。 */
    fun load(pluginId: String) {
        if (loadedPluginId == pluginId) return
        loadedPluginId = pluginId
        val descriptor = repository.configDescriptor(pluginId)
        val stored = repository.configValues(pluginId)
        _state.value = PluginConfigUiState(
            pluginId = pluginId,
            fields = descriptor?.fields.orEmpty().map { field ->
                PluginConfigFieldUiState(
                    key = field.key,
                    label = field.label,
                    kind = field.kind,
                    secret = field.secret,
                    options = field.options,
                    value = stored[field.key] ?: field.defaultValue,
                )
            },
        )
    }

    fun onAction(action: PluginConfigAction) {
        when (action) {
            is PluginConfigAction.SetValue -> _state.update { state ->
                state.copy(
                    fields = state.fields.map { field ->
                        if (field.key == action.key) field.copy(value = action.value) else field
                    },
                )
            }
            PluginConfigAction.Save -> {
                val values = _state.value.fields.associate { field -> field.key to field.value }
                repository.updateConfig(_state.value.pluginId, values)
            }
        }
    }
}
