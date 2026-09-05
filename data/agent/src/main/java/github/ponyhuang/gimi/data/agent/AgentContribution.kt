package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource

/**
 * Agent 构建期的一个能力贡献方。
 *
 * 每个能力（本地工具、MCP、官方工具、技能、插件、记忆、模型目录）自包含地声明：
 * - 进入 Agent 运行时缓存键的配置 revision（[revision]）；
 * - 各工具访问模式下直接声明 / 检索候选的工具集合；
 * - 供推荐等旁路复用的扁平工具目录（[toolCatalog]）。
 *
 * 新增配置源时只需新增实现并通过 Hilt `@IntoSet` 多绑定注册，[AgentFactory]、
 * 缓存键组合与旁路聚合随之自动生效，无需修改任何编排代码。
 */
interface AgentContribution {

    /** 稳定唯一标识；注册表按 id 排序保证装配与缓存键组合的确定性。 */
    val id: String

    /**
     * 当前配置 revision；任一贡献方 revision 变化即触发 Agent 重建。
     * 无外部可变配置的贡献方返回 null（以常量参与缓存键）。
     */
    fun revision(): Any?

    /**
     * 直接声明在 LlmAgent.toolsets 上的工具集。
     *
     * [ToolAccessMode.ALWAYS_AVAILABLE] 返回常驻工具集；ON_DEMAND 模式由各贡献方
     * 自行取舍（通常只保留必须直接声明的部分，其余转为检索候选源）。
     */
    fun toolsets(spec: AgentBuildSpec): List<Toolset> = emptyList()

    /** 直接附加到 LlmAgent.tools 的基础工具；与访问模式无关的贡献在此返回。 */
    fun tools(spec: AgentBuildSpec): List<BaseTool> = emptyList()

    /** ON_DEMAND 模式下进入 `ToolSearchToolset` 的检索候选源。 */
    suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> = emptyList()

    /**
     * 展开当前全部可用工具的扁平目录（只枚举声明、不执行工具），供推荐生成、
     * 能力目录等旁路聚合；与 Agent 构建解耦，不参与目录的贡献方返回空列表。
     */
    suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        emptyList()
}

/**
 * 单个贡献方展开出的扁平工具目录条目。
 *
 * @property source 归因标签（如 "local"、"plugin"、"mcp:服务器名"），推荐能力目录据此展示来源。
 * @property tools 该来源当前可枚举的全部工具。
 */
data class AgentToolCatalogEntry(
    val source: String,
    val tools: List<BaseTool>,
) {
    companion object {
        /** 归因标签：本地系统工具（[github.ponyhuang.gimi.data.agent.contribution.LocalToolContribution]）。 */
        const val SOURCE_LOCAL: String = "local"

        /** 归因标签：动态 APK 插件（[github.ponyhuang.gimi.data.agent.contribution.PluginToolContribution]）。 */
        const val SOURCE_PLUGIN: String = "plugin"
    }
}

/**
 * 工具目录展开上下文。
 *
 * @property modelRuntime 旁路调用方解析出的非敏感模型运行信息；官方工具集的函数
 *   声明依赖当前模型服务，为 null 时官方贡献方跳过展开。
 */
data class AgentToolCatalogContext(
    val modelRuntime: ModelRuntimeMetadata?,
)
