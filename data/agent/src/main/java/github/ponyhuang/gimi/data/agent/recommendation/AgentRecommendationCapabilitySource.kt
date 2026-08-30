package github.ponyhuang.gimi.data.agent.recommendation

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.LocalToolCatalog
import github.ponyhuang.gimi.data.agent.McpToolsetRegistry
import github.ponyhuang.gimi.data.agent.toRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCapability
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationCapabilitySource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import javax.inject.Inject
import javax.inject.Singleton

/** 汇总当前可用工具的声明元数据；该过程不会执行工具。 */
@Singleton
class AgentRecommendationCapabilitySource @Inject constructor(
    private val localTools: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val pluginRuntimeProvider: PluginRuntimeProvider<AgentPlugin>,
    private val mcpRegistry: McpToolsetRegistry,
    private val officialToolsets: Set<@JvmSuppressWildcards OfficialToolset>,
    private val modelFactory: AgentLLMModelFactory,
) : RecommendationCapabilitySource {
    override suspend fun capabilities(): List<RecommendationCapability> = buildList {
        val pluginRuntime = pluginRuntimeProvider.runtime.value
        val enabledLocalIds = toolAuthorization.enabledToolIds()
        localTools.definitions()
            .filter { definition -> definition.id in enabledLocalIds }
            .forEach { definition ->
                add(RecommendationCapability(definition.id, "local", definition.description))
            }
        pluginRuntime.enabledPlugins.flatMap { plugin -> plugin.tools() }
            .forEach { tool -> add(tool.toCapability("plugin")) }
        pluginRuntime.enabledPlugins.flatMap { plugin -> plugin.toolSets() }.forEach { toolset ->
            toolset.getTools(null).forEach { tool -> add(tool.toCapability("plugin")) }
        }
        // 推荐目录需要覆盖全部已配置 MCP server；会话/全局启用筛选只在实际 Agent 执行时应用。
        mcpRegistry.resolveAll().handles.forEach { handle ->
            handle.toolset.getTools(null).forEach { tool ->
                add(tool.toCapability("mcp:${handle.displayName}"))
            }
        }
        runCatching {
            val runtime = (modelFactory.selectFastModelConfig() ?: modelFactory.selectModelConfig(null))
                .toRuntimeMetadata()
            officialToolsets.forEach { toolset ->
                toolset.resolveTools(runtime, null).forEach { tool ->
                    add(tool.toCapability("official:${runtime.serviceId}"))
                }
            }
        }
    }.distinctBy { capability -> "${capability.source}:${capability.id}" }

    private fun BaseTool.toCapability(source: String) = RecommendationCapability(
        id = name,
        source = source,
        description = description.take(MAX_DESCRIPTION_LENGTH),
    )

    private companion object {
        const val MAX_DESCRIPTION_LENGTH: Int = 240
    }
}
