package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.feature.plugin.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PluginConfigViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginConfigUiState())
    val state: StateFlow<PluginConfigUiState> = _state.asStateFlow()
    private val mutableEffects = MutableSharedFlow<PluginConfigEffect>()
    val effects: SharedFlow<PluginConfigEffect> = mutableEffects.asSharedFlow()

    /**
     * 按 pluginId 载入配置描述、已存值与动作。每次进入配置页都从 repository 重新读，
     * 不做幂等跳过：ViewModel 被跨配置页复用（非 per-entry 作用域），若用 loadedPluginId
     * 守卫跳过重载，连续重开同一插件会显示未保存的旧值，看起来像配置被清空。
     * 编辑中的覆盖问题由 LaunchedEffect(pluginId) 只在 pluginId 变化时触发来兜底。
     */
    fun load(pluginId: String) {
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
                viewModelScope.launch {
                    mutableEffects.emit(PluginConfigEffect.ShowToast(messageRes = R.string.plugin_config_saved))
                }
            }
            is PluginConfigAction.RunAction -> runAction(action.actionId)
            is PluginConfigAction.ReceiveActionCallback -> receiveActionCallback(action.actionId, action.values)
            PluginConfigAction.DismissActionCallback -> _state.update { it.copy(callback = null) }
        }
    }

    /**
     * 执行配置页动作。插件可直接返回最终结果，或请求宿主进入交互页并等待通用参数回调。
     */
    private fun runAction(actionId: String) {
        if (_state.value.isAnyActionRunning) return
        viewModelScope.launch {
            _state.update { state ->
                state.copy(
                    actions = state.actions.map { action ->
                        if (action.id == actionId) action.copy(running = true) else action
                    },
                )
            }
            val execution = repository.runAction(_state.value.pluginId, actionId)
            _state.update { state ->
                state.copy(
                    actions = state.actions.map { action ->
                        if (action.id == actionId) action.copy(running = false) else action
                    },
                )
            }
            when (execution) {
                is PluginActionExecution.Completed -> {
                    mutableEffects.emit(PluginConfigEffect.ShowToast(message = execution.outcome.message))
                }
                is PluginActionExecution.AwaitingCallback -> {
                    val request = execution.request
                    _state.update {
                        it.copy(
                            callback = PluginActionCallbackUiState(
                                actionId = actionId,
                                handlerId = request.handlerId,
                                parameters = request.parameters,
                            ),
                        )
                    }
                }
                null -> Unit
            }
        }
    }

    /** 把宿主交互生成的通用参数信封回传插件。 */
    private fun receiveActionCallback(actionId: String, values: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(callback = null) }
            val outcome = repository.onActionCallback(
                pluginId = _state.value.pluginId,
                actionId = actionId,
                callback = PluginActionCallback(values),
            )
            outcome?.let { mutableEffects.emit(PluginConfigEffect.ShowToast(message = it.message)) }
        }
    }
}
