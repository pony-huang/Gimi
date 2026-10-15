package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RecommendationModelConfigTest {
    private val modelServices = mockk<AgentModelConfigurationSource>()
    private val factory = AgentLLMModelFactory(modelServices)

    @Test
    fun deepSeekAnthropicRecommendationUsesItsConfiguredChatCompletionsEndpoint() {
        every { modelServices.currentServices() } returns listOf(
            service("deepseek", "https://custom.deepseek.example/v1/"),
        )
        val original = config("deepseek", ApiProtocol.Anthropic)

        val selected = factory.forRecommendationJson(original)

        assertEquals(ApiProtocol.OpenaiChatcompletions, selected.baseType)
        assertEquals("https://custom.deepseek.example/v1", selected.fullBaseUrl)
        assertEquals(original.modelId, selected.modelId)
        assertEquals(original.apiKey, selected.apiKey)
    }

    @Test
    fun anyCompatibleFastModelUsesItsConfiguredChatCompletionsEndpoint() {
        every { modelServices.currentServices() } returns listOf(
            service("minimax", "https://custom.minimax.example/v1/"),
        )
        val selected = factory.forRecommendationJson(config("minimax", ApiProtocol.Anthropic))

        assertEquals(ApiProtocol.OpenaiChatcompletions, selected.baseType)
        assertEquals("https://custom.minimax.example/v1", selected.fullBaseUrl)
    }

    @Test
    fun alreadyChatCompletionsFastModelKeepsItsEndpoint() {
        val original = config("deepseek", ApiProtocol.OpenaiChatcompletions)

        assertSame(original, factory.forRecommendationJson(original))
    }

    @Test
    fun nativeClaudeCannotBeUsedForOpenAiFormattedOutput() {
        every { modelServices.currentServices() } returns listOf(
            service("anthropic", "https://api.anthropic.com").copy(
                supportedProtocols = listOf(ApiProtocol.Anthropic),
            ),
        )
        val original = config("anthropic", ApiProtocol.Anthropic)

        assertThrows(IllegalStateException::class.java) {
            factory.forRecommendationJson(original)
        }
    }

    @Test
    fun missingChatCompletionsEndpointCannotSilentlyUseAnthropicFormat() {
        every { modelServices.currentServices() } returns listOf(service("deepseek", ""))

        assertThrows(IllegalStateException::class.java) {
            factory.forRecommendationJson(config("deepseek", ApiProtocol.Anthropic))
        }
    }

    private fun config(serviceId: String, protocol: ApiProtocol) = ModelConfig(
        serviceId = serviceId,
        baseType = protocol,
        modelId = "test-model",
        apiKey = "test-key",
        fullBaseUrl = "https://example.com/anthropic",
    )

    private fun service(id: String, chatCompletionsUrl: String) = LLMModelSetting(
        id = id,
        name = id,
        isEnabled = true,
        apiKey = "test-key",
        apiBaseUrl = chatCompletionsUrl,
        apiProtocol = ApiProtocol.Anthropic,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = emptyList(),
    )
}
