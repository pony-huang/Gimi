package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.McpToolsetHandle
import github.ponyhuang.asssistantai.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.asssistantai.agent.tools.toolConfigurationOrNull
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset

/**
 * 单个 MCP server 在 [ToolSearchToolset] 中的候选来源。
 *
 * 与旧的"整体 MCP 来源"（聚合所有 server）相比，单 server source 让以下场景更可控：
 * - 模型 `tool_search` 命中时直接拿到 server 名称（如"filesystem""github"），便于按来源细看；
 * - registry 单 server 发现失败时只丢弃对应 source，其它 server 仍暴露。
 *
 * 创建方是 [github.ponyhuang.asssistantai.agent.AgentFactory.createSearchAgent]，
 * 每次构建按 [McpToolsetRegistry] 的解析结果一次性注册；registry 内部会缓存同一组
 * 服务器选择的 Toolset，因此重复触发 `getTools` 不会重建连接。
 */
internal class McpServerSource(
    private val handle: McpToolsetHandle,
) : DynamicToolCandidateSource {
    override val id: String = "mcp:${handle.serverId}"
    override val displayName: String = handle.displayName

    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        handle.toolset.getTools(readonlyContext)
}

/**
 * 通用 [Toolset] 适配器：把任意 ADK [Toolset] 暴露为 [DynamicToolCandidateSource]。
 *
 * 用于 search 模式以外的"想再外加一个 Toolset 当 source"的场合。MCP 单独的
 * [McpServerSource] 不走这里 —— 它每个 server 一份，保持失败隔离与单一来源归类。
 */
internal class ToolsetCandidateSource(
    override val id: String,
    override val displayName: String,
    private val toolset: Toolset,
) : DynamicToolCandidateSource {
    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.getTools(readonlyContext)
}

/**
 * 把官方函数工具集适配为动态候选来源。
 *
 * 会话级函数选择通过 invocation 上下文（RunConfig metadata）按请求读取；
 * 动态目录只决定这些函数何时把 schema 暴露给模型，不再次解释厂商协议。
 */
internal class OfficialToolCandidateSource(
    private val toolset: DynamicOfficialToolset,
) : DynamicToolCandidateSource {
    override val id: String = toolset.sourceId
    override val displayName: String = toolset.sourceDisplayName

    override suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val modelRuntime = readonlyContext.modelRuntimeMetadataOrNull() ?: return emptyList()
        return toolset.resolveTools(modelRuntime, readonlyContext.toolConfigurationOrNull())
    }
}
