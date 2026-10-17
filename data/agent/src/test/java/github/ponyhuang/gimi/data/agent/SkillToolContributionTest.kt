package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.skills.SkillSource
import github.ponyhuang.gimi.data.agent.contribution.SkillToolContribution
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillToolContributionTest {
    @Test
    fun exposesSkillDiscoveryAndReadingWithoutEnvironmentToolsInBothAccessModes() = runTest {
        val contribution = SkillToolContribution(mockk<SkillSource>())
        val expected = setOf("list_skills", "load_skill", "load_skill_resource")
        for (mode in ToolAccessMode.entries) {
            val spec = AgentBuildSpec(toolAccessMode = mode, pluginRuntime = mockk())
            val names = contribution.toolsets(spec).single().getTools(null).map { it.name }.toSet()
            assertEquals(expected, names)
        }
    }
}
