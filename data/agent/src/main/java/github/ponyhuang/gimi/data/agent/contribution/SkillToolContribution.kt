package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.tools.SkillToolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 技能贡献方：把 [SkillSource] 暴露的技能目录包装为 ADK [SkillToolset] 直接声明，
 * 两种访问模式一致。技能目录由文件系统动态扫描，无构建期 revision。
 */
@Singleton
class SkillToolContribution @Inject constructor(
    private val skillSource: SkillSource,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = listOf(SkillToolset(skillSource))

    private companion object {
        const val ID: String = "skill"
    }
}
