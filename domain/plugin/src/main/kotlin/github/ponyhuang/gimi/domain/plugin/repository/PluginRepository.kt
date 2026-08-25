package github.ponyhuang.gimi.domain.plugin.repository

import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import kotlinx.coroutines.flow.StateFlow

/**
 * 动态插件管理契约 — 宿主侧读取已加载插件、启停与配置。
 */
interface PluginRepository {
    /** 已加载插件描述列表（含启停状态）。 */
    val plugins: StateFlow<List<PluginDescriptor>>

    /** 配置版本，随启停/配置变化递增，供 Agent 运行时缓存失效重建。 */
    val revision: StateFlow<Long>

    /** 启用或关闭指定插件。 */
    fun setEnabled(pluginId: String, enabled: Boolean)

    /** 返回插件的配置描述；无配置或插件不存在时返回 null。 */
    fun configDescriptor(pluginId: String): PluginConfigDescriptor?

    /** 返回插件当前的持久化配置值（key -> value）。 */
    fun configValues(pluginId: String): Map<String, String>

    /** 保存配置并回填到插件实例。 */
    fun updateConfig(pluginId: String, values: Map<String, String>)
}
