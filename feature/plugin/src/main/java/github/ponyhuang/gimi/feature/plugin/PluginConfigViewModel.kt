package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.feature.plugin.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PluginConfigViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginConfigUiState())
    val state: StateFlow<PluginConfigUiState> = _state.asStateFlow()

    private var loadedPluginId: String? = null

    /** 按 pluginId 载入配置描述、已存值与动作；同一插件幂等，避免重复加载覆盖编辑中的状态。 */
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
            actions = descriptor?.actions.orEmpty().map { action ->
                PluginActionUiState(id = action.id, label = action.label)
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
                // 保存成功提示，避免无反馈。
                _state.update { it.copy(notice = PluginNotice(messageRes = R.string.plugin_config_saved)) }
            }
            is PluginConfigAction.RunAction -> runAction(action.actionId)
            is PluginConfigAction.CompleteAction -> completeAction(action.actionId, action.redirectUrl)
            PluginConfigAction.CloseBrowser -> _state.update { it.copy(browser = null) }
            PluginConfigAction.DismissNotice -> _state.update { it.copy(notice = null) }
        }
    }

    /**
     * 执行配置页动作。需要内置浏览器授权的动作（[PluginRepository.configActionBrowserRequest]
     * 非空）进入 WebView 授权页，等截获回调后走 [completeAction]；否则走阻塞路径
     * [PluginRepository.runAction]（实现方负责 IO/挂起）。
     */
    private fun runAction(actionId: String) {
        if (_state.value.isAnyActionRunning) return
        repository.configActionBrowserRequest(_state.value.pluginId, actionId)?.let { request ->
            _state.update {
                it.copy(browser = PluginBrowserUiState(actionId, request.authorizeUrl, request.redirectBase))
            }
            return
        }
        viewModelScope.launch {
            _state.update { state ->
                state.copy(
                    notice = null,
                    actions = state.actions.map { action ->
                        if (action.id == actionId) action.copy(running = true) else action
                    },
                )
            }
            val outcome = repository.runAction(_state.value.pluginId, actionId)
            _state.update { state ->
                state.copy(
                    actions = state.actions.map { action ->
                        if (action.id == actionId) action.copy(running = false) else action
                    },
                    notice = outcome?.let { PluginNotice(it.message, isError = !it.success) },
                )
            }
        }
    }

    /** 内置浏览器截获重定向后，把授权码交给插件完成（如换 token）。 */
    private fun completeAction(actionId: String, redirectUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(browser = null) }
            val outcome = repository.completeAction(_state.value.pluginId, actionId, redirectUrl)
            _state.update { state ->
                state.copy(notice = outcome?.let { PluginNotice(it.message, isError = !it.success) })
            }
        }
    }
}
