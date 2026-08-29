package github.ponyhuang.gimi.agent

import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin

/**
 * 一次 Agent runtime 查找所使用的外部配置快照。
 *
 * @property revision 除插件运行时外的组合配置修订值，用于 Agent 缓存键。
 * @property pluginRuntime 与本次缓存查找和构建绑定的插件运行时快照。
 */
data class AgentBuildConfigurationSnapshot(
    val revision: Any,
    val pluginRuntime: PluginRuntimeSnapshot<AgentPlugin>,
)
