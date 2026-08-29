package github.ponyhuang.gimi.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import kotlinx.coroutines.flow.MutableStateFlow

/** 提供不依赖具体 data 管理器的 Agent 插件运行时测试对象。 */
internal class FakePluginRuntimeProvider(
    plugins: List<AgentPlugin> = emptyList(),
    revision: Long = 0L,
) : PluginRuntimeProvider<AgentPlugin> {
    private val state = MutableStateFlow(
        PluginRuntimeSnapshot(revision = revision, enabledPlugins = plugins),
    )

    override val runtime = state

    fun publish(revision: Long, plugins: List<AgentPlugin>) {
        state.value = PluginRuntimeSnapshot(revision = revision, enabledPlugins = plugins)
    }
}

/** 以固定工具和 Toolset 模拟插件贡献，便于断言 Agent 投影来源。 */
internal class FakeAgentPlugin(
    override val pluginId: String,
    private val pluginTools: List<BaseTool> = emptyList(),
    private val pluginToolsets: List<Toolset> = emptyList(),
) : AgentPlugin {
    override val version: Int = 1
    override val config: PluginConfig = PluginConfig()

    override fun tools(): List<BaseTool> = pluginTools

    override fun toolSets(): List<Toolset> = pluginToolsets
}
