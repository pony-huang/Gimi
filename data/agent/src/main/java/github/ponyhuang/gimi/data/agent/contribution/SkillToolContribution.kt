package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.tools.SkillToolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import javax.inject.Inject
import javax.inject.Singleton
import com.google.adk.kt.annotations.ExperimentalEnvironmentApi
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxEnvironment
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository

/**
 * 技能贡献方：把 [SkillSource] 暴露的技能目录包装为 ADK [SkillToolset] 直接声明，
 * 两种访问模式一致。技能目录由文件系统动态扫描；执行环境的开关 revision
 * 由同注册表的 SandboxToolContribution 记录，脚本能力随 Agent 重建更新。
 */
@Singleton
@OptIn(ExperimentalEnvironmentApi::class)
class SkillToolContribution @Inject constructor(
    private val skillSource: SkillSource,
    private val settings: SandboxSettingsRepository,
    private val environment: SandboxEnvironment,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = listOf(
        if (settings.configuration.value.isReady) SkillToolset(skillSource, environment) else SkillToolset(skillSource),
    )

    private companion object {
        const val ID: String = "skill"
    }
}
