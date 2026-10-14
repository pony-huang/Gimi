package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.contribution.SandboxToolContribution
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxTools
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxEnvironment
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SandboxToolContributionTest {
    private class Settings : SandboxSettingsRepository {
        override val configuration = MutableStateFlow(SandboxConfiguration(true, SandboxConnection(apiKey = "key")))
        override val revision = MutableStateFlow(0L)
        override suspend fun setEnabled(enabled: Boolean) { configuration.value = configuration.value.copy(enabled = enabled); revision.value++ }
        override suspend fun saveConnection(connection: SandboxConnection) { configuration.value = configuration.value.copy(connection = connection); revision.value++ }
    }
    private val settings = Settings()
    private val execution = mockk<SandboxExecutionRepository>()
    private val tools = SandboxTools(settings, execution)
    private val contribution = SandboxToolContribution(tools, settings, SandboxEnvironment(execution))
    private fun spec(mode: ToolAccessMode) = AgentBuildSpec(toolAccessMode = mode, pluginRuntime = mockk())

    @Test fun allToolsModeDeclaresToolsetAndNoSearchCandidate() = runTest {
        assertEquals(2, contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).size)
        assertTrue(contribution.candidateSources(spec(ToolAccessMode.ALWAYS_AVAILABLE)).isEmpty())
        assertEquals(10, contribution.toolCatalog(AgentToolCatalogContext(null)).single().tools.size)
    }
    @Test fun onDemandModeUsesSearchSourceAndRechecksItsAvailability() = runTest {
        val spec = spec(ToolAccessMode.ON_DEMAND)
        assertTrue(contribution.toolsets(spec).isEmpty())
        val sources = contribution.candidateSources(spec)
        val source = sources.first()
        assertEquals("sandbox:e2b", source.id)
        assertEquals(6, source.loadEnabledTools(null).size)
        settings.setEnabled(false)
        assertTrue(source.loadEnabledTools(null).isEmpty())
        assertTrue(sources.last().loadEnabledTools(null).isEmpty())
        assertTrue(contribution.candidateSources(spec).isEmpty())
        assertTrue(contribution.toolCatalog(AgentToolCatalogContext(null)).isEmpty())
    }
    @Test fun successfulConfigurationRevisionChangesContributionCacheKey() = runTest {
        val registry = AgentContributionRegistry(setOf(contribution))
        val previousKey = registry.revision()
        assertEquals(0L, contribution.revision())
        settings.saveConnection(SandboxConnection(apiKey = "new"))
        assertEquals(1L, contribution.revision())
        assertNotEquals(previousKey, registry.revision())
    }
    @Test fun pendingConfigurationNeverDeclaresExecutableTools() = runTest {
        settings.configuration.value = SandboxConfiguration(enabled = true)
        assertTrue(contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).isEmpty())
        assertTrue(contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND)).isEmpty())
    }
}
