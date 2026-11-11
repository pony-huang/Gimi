package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 维护已加载插件、禁用集合与单调递增运行时快照的纯 Kotlin 状态容器。
 *
 * 可能从广播监听（Dispatchers.IO）与 UI 线程并发调用，全部状态读写都在
 * [lock] 内完成；快照经 StateFlow 发布， revision 单调递增由锁保证。
 */
internal class PluginRuntimeState<T : Any>(
    initialPlugins: List<T>,
    initialDisabledPluginIds: Set<String>,
    private val pluginId: (T) -> String,
) {
    private val lock = Any()
    private var loadedPlugins: List<T> = initialPlugins.toList()
    private val disabledPluginIds: MutableSet<String> = initialDisabledPluginIds.toMutableSet()
    private val mutableRuntime = MutableStateFlow(snapshot(revision = 0L))

    val runtime: StateFlow<PluginRuntimeSnapshot<T>> = mutableRuntime.asStateFlow()

    fun setEnabled(id: String, enabled: Boolean): Boolean = synchronized(lock) {
        if (loadedPlugins.none { pluginId(it) == id }) {
            false
        } else {
            val changed = if (enabled) disabledPluginIds.remove(id) else disabledPluginIds.add(id)
            if (changed) publishNextSnapshot()
            changed
        }
    }

    fun replacePlugins(plugins: List<T>): Boolean = synchronized(lock) {
        // 同 id 的新实例表示 APK 已替换，必须发布新快照让 Agent 丢弃旧工具。
        // 按 id 映射做实例比较，列表顺序变化不算变更。
        if (plugins.size == loadedPlugins.size && plugins.all { new ->
                loadedPlugins.firstOrNull { pluginId(it) == pluginId(new) } === new
            }
        ) {
            false
        } else {
            loadedPlugins = plugins.toList()
            publishNextSnapshot()
            true
        }
    }

    fun markConfigurationChanged(id: String): Boolean = synchronized(lock) {
        if (loadedPlugins.none { pluginId(it) == id }) {
            false
        } else {
            publishNextSnapshot()
            true
        }
    }

    fun isEnabled(id: String): Boolean = synchronized(lock) { id !in disabledPluginIds }

    fun disabledPluginIds(): Set<String> = synchronized(lock) { disabledPluginIds.toSet() }

    /** 仅在持有 [lock] 时调用。 */
    private fun publishNextSnapshot() {
        mutableRuntime.value = snapshot(revision = mutableRuntime.value.revision + 1)
    }

    /** 仅在持有 [lock] 时调用。 */
    private fun snapshot(revision: Long): PluginRuntimeSnapshot<T> {
        val enabled = loadedPlugins.filter { pluginId(it) !in disabledPluginIds }
        // 对外列表不可强转后修改，避免消费者反向污染 manager 内部状态。
        val readOnly = Collections.unmodifiableList(ArrayList(enabled))
        return PluginRuntimeSnapshot(revision = revision, enabledPlugins = readOnly)
    }
}
