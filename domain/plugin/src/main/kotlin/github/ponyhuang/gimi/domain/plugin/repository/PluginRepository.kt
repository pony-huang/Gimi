package github.ponyhuang.gimi.domain.plugin.repository

import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
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

    /**
     * 执行插件配置页动作（如「授权登录」），返回结果消息。
     *
     * @return 插件不存在或动作未知时返回 null。
     */
    suspend fun runAction(pluginId: String, actionId: String): PluginActionExecution?

    /**
     * 重新发现已安装插件，无需重启即把新装插件纳入列表与 Agent 运行。
     *
     * @return 本次新增的插件 id 列表（空表示无新增）。
     */
    suspend fun refresh(): List<String>

    /**
     * 把宿主能力产生的通用参数回传插件，完成等待中的配置动作。
     *
     * @return 插件不存在或动作未知时返回 null。
     */
    suspend fun onActionCallback(
        pluginId: String,
        actionId: String,
        callback: PluginActionCallback,
    ): PluginActionOutcome?
}
