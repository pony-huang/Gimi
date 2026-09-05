package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.McpToolsetRegistry
import github.ponyhuang.gimi.data.agent.McpToolsetResolution
import github.ponyhuang.gimi.data.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.gimi.data.agent.tools.mcp.McpAuthorizationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpManualConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.search.McpServerSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 工具贡献方。
 *
 * - revision 跟随 MCP 服务器配置；
 * - ALWAYS_AVAILABLE 模式直接声明 [ConversationMcpToolset]（按请求解析会话选择），
 *   ON_DEMAND 模式把每个 server 拆成独立检索候选源，单个 server 发现失败只丢弃该来源；
 * - MCP 导入 / 授权 / 手工配置三个维护工具在两种模式下都直接声明，供模型自主管理 MCP；
 * - 目录展开覆盖全部已配置 server（发现目录不应用启用开关，会话/全局筛选在实际
 *   Agent 执行时应用）。
 */
@Singleton
class McpToolContribution @Inject constructor(
    private val conversationMcpToolset: ConversationMcpToolset,
    private val mcpToolsetRegistry: McpToolsetRegistry,
    private val mcpConfigurationTool: McpConfigurationTool,
    private val mcpAuthorizationTool: McpAuthorizationTool,
    private val mcpManualConfigurationTool: McpManualConfigurationTool,
    private val mcpRepository: McpRepository,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any = mcpRepository.revision.value

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
            listOf(conversationMcpToolset)
        } else {
            emptyList()
        }

    override fun tools(spec: AgentBuildSpec): List<BaseTool> = listOf(
        mcpConfigurationTool,
        mcpAuthorizationTool,
        mcpManualConfigurationTool,
    )

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> {
        if (spec.toolAccessMode != ToolAccessMode.ON_DEMAND) return emptyList()
        // Agent 构建路径保持既有语义：解析失败直接抛出，由发送入口统一报错。
        return mcpToolsetRegistry.resolveAll().handles.map { handle -> McpServerSource(handle) }
    }

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> {
        // 旁路目录是展示型聚合，解析失败退化为空目录而不是让推荐/能力目录整体失败。
        val resolution = cancellationAwareRunCatching { mcpToolsetRegistry.resolveAll() }
            .getOrDefault(McpToolsetResolution(emptyList()))
        return resolution.handles.map { handle ->
            AgentToolCatalogEntry(
                source = "mcp:${handle.displayName}",
                tools = runCatching { handle.toolset.getTools(null) }.getOrDefault(emptyList()),
            )
        }
    }

    private companion object {
        const val ID: String = "mcp"
    }
}
