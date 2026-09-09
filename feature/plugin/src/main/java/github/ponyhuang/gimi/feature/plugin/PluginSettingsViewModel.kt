package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
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
    private val pendingUninstallPluginId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PluginSettingsUiState> = combine(
        repository.plugins,
        isRefreshing,
        pendingUninstallPluginId,
    ) { plugins, refreshing, uninstallId ->
        PluginSettingsUiState(
            plugins = plugins,
            isRefreshing = refreshing,
            pendingUninstallPluginId = uninstallId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PluginSettingsUiState(
            plugins = repository.plugins.value,
            pendingUninstallPluginId = pendingUninstallPluginId.value,
        ),
    )

    // 使用 SharedFlow + 缓冲实现一次性 UI 事件，避免默认参数下「无订阅者时 emit 挂起」导致事件丢失：
    // - extraBufferCapacity：缓存若干待发事件，订阅者重新挂载时不会丢；
    // - onBufferOverflow.DROP_OLDEST：缓冲满时丢弃最旧的事件，保证内存有界（UI 事件短暂过期即可）。
    private val mutableEffects = MutableSharedFlow<PluginSettingsEffect>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<PluginSettingsEffect> = mutableEffects.asSharedFlow()

    fun onAction(action: PluginSettingsAction) {
        when (action) {
            is PluginSettingsAction.SetEnabled ->
                repository.setEnabled(action.pluginId, action.enabled)
            PluginSettingsAction.Refresh -> refresh()
            is PluginSettingsAction.RequestUninstall -> requestUninstall(action.pluginId)
            PluginSettingsAction.DismissUninstall -> pendingUninstallPluginId.value = null
            is PluginSettingsAction.ConfirmUninstall -> confirmUninstall(action.pluginId)
        }
    }

    /** 标记待卸载插件以弹出确认框；未知插件直接忽略，不弹框。 */
    private fun requestUninstall(pluginId: String) {
        if (repository.plugins.value.any { it.id == pluginId }) {
            pendingUninstallPluginId.value = pluginId
        }
    }

    /** 确认卸载：关闭确认框并交由宿主拉起系统卸载页。 */
    private fun confirmUninstall(pluginId: String) {
        pendingUninstallPluginId.value = null
        val packageName = repository.plugins.value
            .firstOrNull { it.id == pluginId }
            ?.packageName
            ?: return
        viewModelScope.launch {
            mutableEffects.emit(PluginSettingsEffect.RequestSystemUninstall(packageName))
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
