package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.skills.SkillSource
import github.ponyhuang.gimi.data.agent.contribution.SkillToolContribution
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxEnvironment
import github.ponyhuang.gimi.domain.sandbox.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 沙箱开关只控制脚本执行，不影响技能发现和读取。 */
class SkillToolContributionTest {
    @Test
    fun readsSkillsAlwaysAndAddsScriptsOnlyForReadyEnvironment() = runTest {
        val configurationState = MutableStateFlow(SandboxConfiguration())
        val settings = mockk<SandboxSettingsRepository> { every { configuration } returns configurationState }
        val contribution = SkillToolContribution(mockk<SkillSource>(), settings, SandboxEnvironment(mockk()))
        val spec = AgentBuildSpec(pluginRuntime = mockk())
        val names = contribution.toolsets(spec).single().getTools(null).map { it.name }.toSet()
        assertEquals(setOf("list_skills", "load_skill", "load_skill_resource"), names)
        configurationState.value = SandboxConfiguration(true, SandboxConnection(apiKey = "test"))
        val enabled = contribution.toolsets(spec).single().getTools(null).map { it.name }.toSet()
        assertEquals(names + "run_skill_script", enabled)
    }
}
