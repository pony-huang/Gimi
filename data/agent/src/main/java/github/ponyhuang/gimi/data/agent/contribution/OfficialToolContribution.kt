package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.SearchOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.search.OfficialToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 官方（厂商内置）工具集贡献方。
 *
 * - 工具集列表由 Hilt 多绑定静态注入，无构建期 revision；
 * - ALWAYS_AVAILABLE 模式全部直接声明；ON_DEMAND 模式下厂商原生工具集保持直接
 *   声明，可展开函数声明的工具集（[SearchOfficialToolset]）转为检索候选源；
 * - 目录展开用调用方提供的模型运行信息解析函数声明（官方函数按当前模型服务门控），
 *   单个工具集解析失败只丢弃该来源。
 */
@Singleton
class OfficialToolContribution @Inject constructor(
    private val officialToolsets: Set<@JvmSuppressWildcards OfficialToolset>,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = when (spec.toolAccessMode) {
        ToolAccessMode.ALWAYS_AVAILABLE -> officialToolsets.toList()
        ToolAccessMode.ON_DEMAND -> officialToolsets.filterNot { it is SearchOfficialToolset }
    }

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> {
        if (spec.toolAccessMode != ToolAccessMode.ON_DEMAND) return emptyList()
        return officialToolsets
            .filterIsInstance<SearchOfficialToolset>()
            .map { toolset -> OfficialToolCandidateSource(toolset) }
    }

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> {
        val modelRuntime = context.modelRuntime ?: return emptyList()
        return officialToolsets.map { toolset ->
            AgentToolCatalogEntry(
                source = "official:${modelRuntime.serviceId}",
                tools = runCatching { toolset.resolveTools(modelRuntime, selection = null) }
                    .getOrDefault(emptyList()),
            )
        }
    }

    private companion object {
        const val ID: String = "official"
    }
}
