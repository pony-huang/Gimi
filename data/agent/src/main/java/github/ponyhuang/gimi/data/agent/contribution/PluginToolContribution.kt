package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolsetCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态 APK 插件贡献方。
 *
 * - revision 取插件运行时快照的 revision（经 [PluginRuntimeProvider]），与装配使用的
 *   快照（[AgentBuildSpec.pluginRuntime]）同源，插件启停 / 重装 / 配置变更后 Agent 重建；
 * - 常驻模式直接声明，按需模式以 Toolset 为单位检索并加载；直接工具独立成组；
 *   每次构建读取当前启用插件，使开关 / 配置在下次请求立即生效；
 * - 目录展开合并插件直接工具与插件 Toolset 动态出的工具，单个 Toolset 展开失败只
 *   丢弃该来源。
 */
@Singleton
class PluginToolContribution @Inject constructor(
    private val pluginRuntimeProvider: PluginRuntimeProvider<AgentPlugin>,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any = pluginRuntimeProvider.runtime.value.revision

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
            spec.pluginRuntime.enabledPlugins.flatMap { plugin -> plugin.toolSets() }
        } else emptyList()

    override fun tools(spec: AgentBuildSpec): List<BaseTool> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
            spec.pluginRuntime.enabledPlugins.flatMap { plugin -> plugin.tools() }
        } else emptyList()

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> {
        if (spec.toolAccessMode != ToolAccessMode.ON_DEMAND) return emptyList()
        return spec.pluginRuntime.enabledPlugins.flatMap { plugin ->
            val directTools = object : Toolset {
                override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = plugin.tools()
            }
            listOf(
                ToolsetCandidateSource("plugin:${plugin.pluginId}:tools", plugin.displayName, directTools, loadAsGroup = true),
            ) + plugin.toolSets().mapIndexed { index, toolset ->
                ToolsetCandidateSource(
                    "plugin:${plugin.pluginId}:toolset:$index",
                    plugin.displayName,
                    toolset,
                    loadAsGroup = true,
                )
            }
        }
    }

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> {
        val runtime = pluginRuntimeProvider.runtime.value
        val directTools = runtime.enabledPlugins.flatMap { plugin -> plugin.tools() }
        val toolsetTools = runtime.enabledPlugins
            .flatMap { plugin -> plugin.toolSets() }
            .flatMap { toolset -> runCatching { toolset.getTools(null) }.getOrDefault(emptyList()) }
        return listOf(AgentToolCatalogEntry(source = AgentToolCatalogEntry.SOURCE_PLUGIN, tools = directTools + toolsetTools))
    }

    private companion object {
        const val ID: String = "plugin"
    }
}
