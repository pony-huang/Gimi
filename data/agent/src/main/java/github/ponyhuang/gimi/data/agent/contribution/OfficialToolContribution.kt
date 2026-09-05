package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.gimi.data.agent.tools.search.OfficialToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 官方(厂商内置)工具集贡献方。
 *
 * - 所有厂商工具由单一 [DefaultOfficialToolset] 在请求期按注册表声明组装,无构建期 revision;
 * - ALWAYS_AVAILABLE 模式全部直接声明;ON_DEMAND 模式下厂商原生工具保持直接声明,
 *   标记为检索候选([OfficialToolSpec.searchCandidate],如 Kimi formulas)的声明
 *   转为 tool_search 检索候选源;
 * - 目录展开用调用方提供的模型运行信息解析函数声明(官方函数按当前模型服务门控),
 *   解析失败只丢弃该来源。
 */
@Singleton
class OfficialToolContribution @Inject constructor(
    private val officialToolset: DefaultOfficialToolset,
    private val registry: OfficialToolRegistry,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = listOf(officialToolset)

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> {
        if (spec.toolAccessMode != ToolAccessMode.ON_DEMAND) return emptyList()
        return registry.all
            .filter { it.searchCandidate }
            .map { candidate -> OfficialToolCandidateSource(candidate, officialToolset) }
    }

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> {
        val modelRuntime = context.modelRuntime ?: return emptyList()
        return listOf(
            AgentToolCatalogEntry(
                source = "official:${modelRuntime.serviceId}",
                tools = runCatching { officialToolset.resolveTools(modelRuntime, selection = null) }
                    .getOrDefault(emptyList()),
            )
        )
    }

    private companion object {
        const val ID: String = "official"
    }
}
