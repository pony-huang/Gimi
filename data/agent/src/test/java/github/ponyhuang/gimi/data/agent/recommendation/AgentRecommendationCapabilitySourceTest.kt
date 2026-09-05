package github.ponyhuang.gimi.data.agent.recommendation

import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.McpToolsetRegistry
import github.ponyhuang.gimi.data.agent.McpToolsetResolution
import github.ponyhuang.gimi.data.agent.contribution.LocalToolContribution
import github.ponyhuang.gimi.data.agent.contribution.McpToolContribution
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

        val toolAuthorization = mockk<ToolAuthorizationRepository> {
            every { revision } returns MutableStateFlow(0L)
            every { enabledToolIds() } returns emptySet()
        }
        // 能力目录经贡献方注册表聚合；MCP 目录展开由 McpToolContribution 负责。
        val registry = AgentContributionRegistry(
            setOf(
                LocalToolContribution(
                    localToolCatalog = mockk(relaxed = true),
                    localToolset = mockk(relaxed = true),
                    toolAuthorization = toolAuthorization,
                ),
                McpToolContribution(
                    conversationMcpToolset = mockk(relaxed = true),
                    mcpToolsetRegistry = mcpRegistry,
                    mcpConfigurationTool = mockk(relaxed = true),
                    mcpAuthorizationTool = mockk(relaxed = true),
                    mcpManualConfigurationTool = mockk(relaxed = true),
                    mcpRepository = mockk<McpRepository> {
                        every { revision } returns MutableStateFlow(0L)
                    },
                ),
            ),
        )

        val source = AgentRecommendationCapabilitySource(
            contributionRegistry = registry,
            toolAuthorization = toolAuthorization,
            modelFactory = modelFactory,
        )

        source.capabilities()

        coVerify(exactly = 1) { mcpRegistry.resolveAll() }
    }
}
