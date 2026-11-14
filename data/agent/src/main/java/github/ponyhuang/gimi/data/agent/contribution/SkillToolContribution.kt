package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.skills.InstallSkillTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 技能贡献方：把 [SkillSource] 暴露的技能目录包装为 ADK [SkillToolset] 直接声明，
 * 两种访问模式一致。技能目录由文件系统动态扫描；安装入口独立于执行环境，脚本执行使用环境工具集。
 */
@Singleton
class SkillToolContribution @Inject constructor(
    private val skillSource: SkillSource,
    private val installer: InstallSkillTool,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun tools(spec: AgentBuildSpec): List<BaseTool> = listOf(installer)

    override suspend fun toolCatalog(context: AgentToolCatalogContext) =
        listOf(AgentToolCatalogEntry("skill:install", listOf(installer)))

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = listOf(
        SkillToolset(skillSource),
    )

    private companion object {
        const val ID: String = "skill"
    }
}
