package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import github.ponyhuang.gimi.data.modelaccess.ModelConfig
import github.ponyhuang.gimi.data.modelaccess.AgentLLMModelFactory

class RecommendationModelConfigTest {
    private val modelServices = mockk<AgentModelConfigurationSource>()
    private val factory = AgentLLMModelFactory(modelServices)

    @Test
    fun deepSeekAnthropicRecommendationUsesItsConfiguredChatCompletionsEndpoint() {
        every { modelServices.currentInstances() } returns listOf(
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
        every { modelServices.currentInstances() } returns listOf(
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
        every { modelServices.currentInstances() } returns listOf(
            // 对话连接只有 Anthropic 协议，没有 OpenAI 兼容端点可用。
            service("anthropic", "https://api.anthropic.com", protocol = ApiProtocol.Anthropic),
        )
        val original = config("anthropic", ApiProtocol.Anthropic)

        assertThrows(IllegalStateException::class.java) {
            factory.forRecommendationJson(original)
        }
    }

    @Test
    fun missingChatCompletionsEndpointCannotSilentlyUseAnthropicFormat() {
        every { modelServices.currentInstances() } returns listOf(service("deepseek", ""))

        assertThrows(IllegalStateException::class.java) {
            factory.forRecommendationJson(config("deepseek", ApiProtocol.Anthropic))
        }
    }

    private fun config(serviceId: String, protocol: ApiProtocol) = ModelConfig(
        serviceId = serviceId,
        templateId = serviceId,
        baseType = protocol,
        modelId = "test-model",
        apiKey = "test-key",
        fullBaseUrl = "https://example.com/anthropic",
    )

    /** 构造仅含一条对话连接的服务实例；protocol 与 endpoint 决定推荐格式化可用的端点。 */
    private fun service(
        id: String,
        chatCompletionsUrl: String,
        protocol: ApiProtocol = ApiProtocol.OpenaiChatcompletions,
    ) = ModelServiceInstance(
        id = id,
        name = id,
        templateId = id,
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = "test-key",
        connections = listOf(
            ServiceConnection(
                id = "conversation",
                capability = ServiceCapability.Conversation,
                protocol = protocol,
                endpoint = chatCompletionsUrl,
                models = listOf(Model(id = "test-model", name = "test-model")),
            ),
        ),
    )
}
