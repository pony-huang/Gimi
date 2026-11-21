package github.ponyhuang.gimi.data.agent.recommendation

import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentModelRuntime
import github.ponyhuang.gimi.data.modelaccess.DefaultAgentLLMModelFactory
import github.ponyhuang.gimi.domain.modelaccess.ModelConfig
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationNotConfiguredException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentRecommendationGeneratorTest {
    @Test
    fun missingFastModelDoesNotFallBackToChatModelForFormattedOutput() = runTest {
        val modelFactory = mockk<DefaultAgentLLMModelFactory>()
        every { modelFactory.selectFastModelConfig() } returns null
        val generator = AgentRecommendationGenerator(
            modelFactory = AgentModelRuntime(modelFactory),
            contributionRegistry = mockk<AgentContributionRegistry>(),
        )

        try {
            generator.generate(
                RecommendationGenerationInput(
                    systemInstruction = "",
                    capabilities = emptyList(),
                    context = RecommendationContext(emptyMap()),
                ),
            )
            fail("A fast model is required for formatted recommendations.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("fast model"))
        }
        verify(exactly = 0) { modelFactory.selectModelConfig(any()) }
    }

    @Test
    fun unsupportedFastModelProtocolFailsAsConfigurationWithoutRetrying() = runTest {
        val modelFactory = mockk<DefaultAgentLLMModelFactory>()
        every { modelFactory.selectFastModelConfig() } returns ModelConfig(
            serviceId = "fast",
            templateId = null,
            baseType = ApiProtocol.Anthropic,
            modelId = "claude",
            apiKey = "key",
            fullBaseUrl = "https://example.com",
        )
        every { modelFactory.forRecommendationJson(any()) } throws IllegalStateException(
            "The fast model service does not support OpenAI-compatible JSON output.",
        )
        val generator = AgentRecommendationGenerator(
            modelFactory = AgentModelRuntime(modelFactory),
            contributionRegistry = mockk<AgentContributionRegistry>(),
        )

        try {
            generator.generate(
                RecommendationGenerationInput(
                    systemInstruction = "",
                    capabilities = emptyList(),
                    context = RecommendationContext(emptyMap()),
                ),
            )
            fail("An unsupported fast-model protocol must fail as a configuration error.")
        } catch (error: RecommendationNotConfiguredException) {
            assertTrue(error.message.orEmpty().contains("OpenAI-compatible"))
        }
    }
}
