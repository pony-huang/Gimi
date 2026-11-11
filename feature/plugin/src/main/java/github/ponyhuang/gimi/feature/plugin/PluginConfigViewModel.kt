package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.feature.plugin.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
                        if (field.key == action.key) {
                            field.copy(value = sanitizeFieldValue(field, action.value))
                        } else {
                            field
                        }
                    },
                )
            }
            PluginConfigAction.Save -> {
                // 落库前按字段声明清洗：select 只允许选项值，文本限长，防止越权/超长值直接 configure 给插件。
                val values = _state.value.fields.associate { field ->
                    field.key to sanitizeFieldValue(field, field.value)
                }
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

    /** SELECT 字段限定 options 内的值；TEXT 限长，避免超长载荷直落持久层。 */
    private fun sanitizeFieldValue(field: PluginConfigFieldUiState, value: String): String = when (field.kind) {
        PluginConfigFieldDescriptor.Kind.SELECT ->
            if (value in field.options) value else field.options.firstOrNull().orEmpty()
        else -> value.take(MAX_FIELD_VALUE_LENGTH)
    }

    /**
     * 执行配置页动作。插件可直接返回最终结果，或请求宿主进入交互页并等待通用参数回调。
     */
    private fun runAction(actionId: String) {
        // 在 update 的原子重算里同时完成互斥判定与置位：连点竞态下最多有一个调用拿到 running 位。
        var claimed = false
        _state.update { state ->
            if (state.isAnyActionRunning || state.actions.none { it.id == actionId }) {
                state
            } else {
                claimed = true
                state.copy(
                    actions = state.actions.map { action ->
                        if (action.id == actionId) action.copy(running = true) else action
                    },
                )
            }
        }
        if (!claimed) return
        viewModelScope.launch {
            try {
                if (actionId == OAUTH_LOGIN_ACTION) {
                    val current = _state.value
                    // 授权必须使用当前表单值，避免用户多点一次保存或误用旧凭证。
                    repository.updateConfig(current.pluginId, current.fields.associate { it.key to it.value })
                }
                val execution = repository.runAction(_state.value.pluginId, actionId)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 动作失败不能静默：不抛出（launch 内异常会崩宿主），以失败提示收尾。
                mutableEffects.emit(
                    PluginConfigEffect.ShowToast(message = error.message ?: error.toString()),
                )
            } finally {
                // 无论成败/取消都必须复位 running 位，否则动作按钮永久禁用。
                _state.update { state ->
                    state.copy(
                        actions = state.actions.map { action ->
                            if (action.id == actionId) action.copy(running = false) else action
                        },
                    )
                }
            }
        }
    }

    /** 把宿主交互生成的通用参数信封回传插件。 */
    private fun receiveActionCallback(actionId: String, values: Map<String, String>) {
        viewModelScope.launch {
            try {
                val outcome = repository.onActionCallback(
                    pluginId = _state.value.pluginId,
                    actionId = actionId,
                    callback = PluginActionCallback(values),
                )
                outcome?.let { mutableEffects.emit(PluginConfigEffect.ShowToast(message = it.message)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableEffects.emit(
                    PluginConfigEffect.ShowToast(message = error.message ?: error.toString()),
                )
            } finally {
                // 回调页只在回传动作完结（成功/失败/取消）后关闭，失败时不丢失交互现场。
                _state.update { it.copy(callback = null) }
            }
        }
    }

    private companion object {
        const val OAUTH_LOGIN_ACTION: String = "oauth_login"
        const val MAX_FIELD_VALUE_LENGTH: Int = 4_096
    }
}
