package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.agent.McpToolsetRegistry
import github.ponyhuang.gimi.agent.tools.toolConfigurationOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按会话选择聚合 MCP 工具的 ADK [Toolset]。
 *
 * 每次模型请求时从 invocation 上下文（RunConfig metadata）读取会话勾选的
 * MCP server ID，委托 [McpToolsetRegistry] 惰性解析；metadata 未携带会话配置时
 * 回退到全局启用集合。连接池化与失败隔离由 registry 与各 [McpToolset] 负责。
 */
@Singleton
class ConversationMcpToolset @Inject constructor(
    private val registry: McpToolsetRegistry,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val selectedServerIds = readonlyContext.toolConfigurationOrNull()?.enabledMcpServerIds
        return registry.resolve(selectedServerIds)
            .handles
            .flatMap { handle -> handle.toolset.getTools(readonlyContext) }
    }
}
