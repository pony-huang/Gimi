package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.LocalToolCatalog
import github.ponyhuang.gimi.data.agent.tools.search.LocalToolSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.data.agent.tools.system.ToolsetConfirmationResumeTool
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具贡献方。
 *
 * - revision 跟随全局工具授权开关；
 * - ALWAYS_AVAILABLE 模式直接声明 [LocalToolset]（内部按请求过滤），ON_DEMAND 模式
 *   转为单个扁平检索候选源；
 * - 需要确认的本地工具额外注册隐藏的确认续接工具，两种模式一致；
 * - 目录展开返回完整本地工具扁平列表（不应用授权过滤，由调用方按需筛选）。
 */
@Singleton
class LocalToolContribution @Inject constructor(
    private val localToolCatalog: LocalToolCatalog,
    private val localToolset: LocalToolset,
    private val toolAuthorization: ToolAuthorizationRepository,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any = toolAuthorization.revision.value

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
            listOf(localToolset)
        } else {
            emptyList()
        }

    override fun tools(spec: AgentBuildSpec): List<BaseTool> =
        localToolCatalog.tools()
            .filter { tool -> tool.name in localToolCatalog.confirmationRequiredToolIds }
            .map { tool -> ToolsetConfirmationResumeTool(localToolset, tool) }

    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        if (spec.toolAccessMode == ToolAccessMode.ON_DEMAND) {
            listOf(LocalToolSource(localToolCatalog, localToolset))
        } else {
            emptyList()
        }

    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        listOf(AgentToolCatalogEntry(source = AgentToolCatalogEntry.SOURCE_LOCAL, tools = localToolCatalog.tools()))

    private companion object {
        const val ID: String = "local"
    }
}
