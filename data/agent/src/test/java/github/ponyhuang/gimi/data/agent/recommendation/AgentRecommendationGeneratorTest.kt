package github.ponyhuang.gimi.data.agent.recommendation

import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
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
        val modelFactory = mockk<AgentLLMModelFactory>()
        every { modelFactory.selectFastModelConfig() } returns null
        val generator = AgentRecommendationGenerator(
            modelFactory = modelFactory,
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
}
