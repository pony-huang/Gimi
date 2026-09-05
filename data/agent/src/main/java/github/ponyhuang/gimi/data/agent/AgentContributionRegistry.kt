package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AgentContribution] 注册表门面。
 *
 * 统一提供三件事：
 * - 确定性聚合顺序：按贡献方 [AgentContribution.id] 排序，屏蔽 Hilt 多绑定 Set 的
 *   无序性，保证 Agent 装配与缓存键组合跨进程稳定；
 * - 缓存键 revision 组合：任一贡献方 revision 变化即整体失效，新增配置源无需再
 *   手工维护 revision 清单；
 * - 面向 Agent 构建（[AgentFactory]）与旁路聚合（推荐生成、能力目录）的统一读取入口。
 */
@Singleton
class AgentContributionRegistry @Inject constructor(
    contributions: Set<@JvmSuppressWildcards AgentContribution>,
) {
    private val sortedContributions: List<AgentContribution> = contributions
        .sortedBy(AgentContribution::id)
        .also { sorted ->
            // id 是缓存键组合与聚合顺序的唯一依据，重复会破坏确定性，构造期快速失败。
            val duplicateIds = sorted.map(AgentContribution::id)
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            require(duplicateIds.isEmpty()) {
                "Duplicate agent contribution ids: ${duplicateIds.joinToString()}"
            }
        }

    /** 全部贡献方 revision 的组合值，作为 Agent 运行时缓存键的配置维度。 */
    fun revision(): Any = sortedContributions.map { contribution -> contribution.id to contribution.revision() }

    /** 聚合各贡献方直接声明的工具集。 */
    fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        sortedContributions.flatMap { contribution -> contribution.toolsets(spec) }

    /** 聚合各贡献方直接声明的基础工具。 */
    fun tools(spec: AgentBuildSpec): List<BaseTool> =
        sortedContributions.flatMap { contribution -> contribution.tools(spec) }

    /** 聚合各贡献方的检索候选源（ON_DEMAND 模式）。 */
    suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        sortedContributions.flatMap { contribution -> contribution.candidateSources(spec) }

    /** 聚合各贡献方的扁平工具目录（旁路聚合用）。 */
    suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        sortedContributions.flatMap { contribution -> contribution.toolCatalog(context) }
}
