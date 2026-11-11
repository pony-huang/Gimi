package github.ponyhuang.gimi.data.agent.phone

import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 直控贡献方装配契约：可用性 gating、缓存键跟随与每次新建子代理实例。 */
class DirectPhoneContributionTest {
    private val spec = AgentBuildSpec(pluginRuntime = PluginRuntimeSnapshot(0L, emptyList<AgentPlugin>()))

    @Test
    fun unsupportedBackendDoesNotExposeDelegationTarget() = runTest {
        val contribution = contribution(MobileUseAvailability.UNSUPPORTED)

        assertEquals("direct_phone", contribution.id)
        assertTrue(contribution.subAgents(spec).isEmpty())
        assertEquals(false, contribution.revision())
    }

    @Test
    fun availableBackendExposesFreshAgentInstancePerAssembly() = runTest {
        val contribution = contribution(MobileUseAvailability.READY)

        val first = contribution.subAgents(spec)
        val second = contribution.subAgents(spec)

        assertEquals(DirectPhoneAgent.NAME, first.single().name)
        // BaseAgent 挂树写入 parentAgent；跨次装配复用同一实例会二次挂载失败。
        assertNotSame(first.single(), second.single())
        assertEquals(true, contribution.revision())
    }

    @Test
    fun availabilityChangeInvalidatesRuntimeCacheKey() {
        val controller = mockk<DirectPhoneController>()
        every { controller.availability() } returnsMany listOf(
            MobileUseAvailability.UNSUPPORTED,
            MobileUseAvailability.READY,
        )
        val contribution = DirectPhoneContribution(controller)

        assertNotEquals(contribution.revision(), contribution.revision())
    }

    @Test
    fun contributesNoTools() = runTest {
        val contribution = contribution(MobileUseAvailability.READY)

        assertTrue(contribution.toolsets(spec).isEmpty())
        assertTrue(contribution.tools(spec).isEmpty())
    }

    private fun contribution(availability: MobileUseAvailability): DirectPhoneContribution {
        val controller = mockk<DirectPhoneController>()
        every { controller.availability() } returns availability
        return DirectPhoneContribution(controller)
    }
}
