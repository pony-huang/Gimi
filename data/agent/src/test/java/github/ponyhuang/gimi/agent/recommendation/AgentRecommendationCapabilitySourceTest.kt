package github.ponyhuang.gimi.agent.recommendation

import github.ponyhuang.gimi.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.agent.LocalToolCatalog
import github.ponyhuang.gimi.agent.McpToolsetRegistry
import github.ponyhuang.gimi.agent.McpToolsetResolution
import github.ponyhuang.gimi.agent.FakePluginRuntimeProvider
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentRecommendationCapabilitySourceTest {
    @Test
    fun discoversToolsFromAllConfiguredMcpServers() = runTest {
        val mcpRegistry = mockk<McpToolsetRegistry>()
        coEvery { mcpRegistry.resolveAll() } returns McpToolsetResolution(emptyList())
        val modelFactory = mockk<AgentLLMModelFactory>()
        every { modelFactory.selectFastModelConfig() } returns null
        every { modelFactory.selectModelConfig(null) } throws IllegalStateException("no model")

        val source = AgentRecommendationCapabilitySource(
            localTools = mockk<LocalToolCatalog>(relaxed = true),
            toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true),
            pluginRuntimeProvider = FakePluginRuntimeProvider(),
            mcpRegistry = mcpRegistry,
            officialToolsets = emptySet(),
            modelFactory = modelFactory,
        )

        source.capabilities()

        coVerify(exactly = 1) { mcpRegistry.resolveAll() }
    }
}
