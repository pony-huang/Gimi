package github.ponyhuang.gimi.data.agent.tools.search

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.McpToolsetHandle
import github.ponyhuang.gimi.data.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.gimi.data.agent.tools.toolConfigurationOrNull
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolSpec
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching

/**
 * 单个 MCP server 在 [ToolSearchToolset] 中的候选来源。
 *
 * 与旧的"整体 MCP 来源"（聚合所有 server）相比，单 server source 让以下场景更可控：
 * - 模型 `tool_search` 命中时直接拿到 server 名称（如"filesystem""github"），便于按来源细看；
 * - registry 单 server 发现失败时只丢弃对应 source，其它 server 仍暴露。
 *
 * 创建方是 [github.ponyhuang.gimi.data.agent.AgentFactory.createSearchAgent]，
 * 每次构建按 [McpToolsetRegistry] 的解析结果一次性注册；registry 内部会缓存同一组
 * 服务器选择的 Toolset，因此重复触发 `getTools` 不会重建连接。
 */
internal class McpServerSource(
    private val handle: McpToolsetHandle,
) : ToolCandidateSource {
    override val id: String = "mcp:${handle.serverId}"
    override val displayName: String = handle.displayName

    override suspend fun loadAllTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        // 单个 server 发现失败（不可达 / 超时）只丢弃该来源，不影响索引中的其它来源。
        cancellationAwareRunCatching { handle.toolset.getTools(readonlyContext) }
            .getOrDefault(emptyList())

    override suspend fun loadEnabledTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> {
        val selectedServerIds = readonlyContext.toolConfigurationOrNull()?.enabledMcpServerIds
        val enabled = selectedServerIds
            ?.let { selected -> handle.serverId in selected }
            ?: handle.isGloballyEnabled
        return if (enabled) loadAllTools(readonlyContext) else emptyList()
    }
}

/**
 * 通用 [Toolset] 适配器：把任意 ADK [Toolset] 暴露为 [ToolCandidateSource]。
 *
 * 用于 search 模式以外的"想再外加一个 Toolset 当 source"的场合。MCP 单独的
 * [McpServerSource] 不走这里 —— 它每个 server 一份，保持失败隔离与单一来源归类。
 */
internal class ToolsetCandidateSource(
    override val id: String,
    override val displayName: String,
    private val toolset: Toolset,
) : ToolCandidateSource {
    override suspend fun loadAllTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        toolset.getTools(readonlyContext)

    override suspend fun loadEnabledTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> = loadAllTools(readonlyContext)
}

/**
 * 把一个官方工具声明适配为动态候选来源。
 *
 * 会话级函数选择通过 invocation 上下文（RunConfig metadata）按请求读取;
 * 动态目录只决定这些函数何时把 schema 暴露给模型,不再次解释厂商协议。
 * 工具构造复用 [DefaultOfficialToolset.resolveSpec],与请求期注入语义一致。
 */
internal class OfficialToolCandidateSource(
    private val spec: OfficialToolSpec,
    private val officialToolset: DefaultOfficialToolset,
) : ToolCandidateSource {
    override val id: String = "official:${spec.toolId}"
    override val displayName: String = spec.displayName

    override suspend fun loadAllTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val modelRuntime = readonlyContext.modelRuntimeMetadataOrNull() ?: return emptyList()
        return officialToolset.resolveSpec(spec, modelRuntime, selection = null)
    }

    override suspend fun loadEnabledTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> {
        val modelRuntime = readonlyContext.modelRuntimeMetadataOrNull() ?: return emptyList()
        return officialToolset.resolveSpec(spec, modelRuntime, readonlyContext.toolConfigurationOrNull())
    }
}
