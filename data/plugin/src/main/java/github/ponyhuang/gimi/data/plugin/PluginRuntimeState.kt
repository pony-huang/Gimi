package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 维护已加载插件、禁用集合与单调递增运行时快照的纯 Kotlin 状态容器。 */
internal class PluginRuntimeState<T : Any>(
    initialPlugins: List<T>,
    initialDisabledPluginIds: Set<String>,
    private val pluginId: (T) -> String,
) {
    private var loadedPlugins: List<T> = initialPlugins.toList()
    private val disabledPluginIds: MutableSet<String> = initialDisabledPluginIds.toMutableSet()
    private val mutableRuntime = MutableStateFlow(snapshot(revision = 0L))

    val runtime: StateFlow<PluginRuntimeSnapshot<T>> = mutableRuntime.asStateFlow()

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        if (loadedPlugins.none { pluginId(it) == id }) return false
        val changed = if (enabled) disabledPluginIds.remove(id) else disabledPluginIds.add(id)
        if (changed) publishNextSnapshot()
        return changed
    }

    fun replacePlugins(plugins: List<T>): Boolean {
        if (loadedPlugins.map(pluginId) == plugins.map(pluginId)) return false
        loadedPlugins = plugins.toList()
        publishNextSnapshot()
        return true
    }

    fun markConfigurationChanged(id: String): Boolean {
        if (loadedPlugins.none { pluginId(it) == id }) return false
        publishNextSnapshot()
        return true
    }

    fun isEnabled(id: String): Boolean = id !in disabledPluginIds

    fun disabledPluginIds(): Set<String> = disabledPluginIds.toSet()

    private fun publishNextSnapshot() {
        mutableRuntime.value = snapshot(revision = mutableRuntime.value.revision + 1)
    }

    private fun snapshot(revision: Long): PluginRuntimeSnapshot<T> {
        val enabled = loadedPlugins.filter { pluginId(it) !in disabledPluginIds }
        // 对外列表不可强转后修改，避免消费者反向污染 manager 内部状态。
        val readOnly = Collections.unmodifiableList(ArrayList(enabled))
        return PluginRuntimeSnapshot(revision = revision, enabledPlugins = readOnly)
    }
}
