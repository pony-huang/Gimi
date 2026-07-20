package github.ponyhuang.asssistantai.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAvailabilityTest {

    @Test
    fun builtInDeepSeekAndMiniMaxStartDisabled() {
        val providers = DefaultModelServices.services.associateBy { it.serviceId }

        assertFalse(providers.getValue("deepseek").isEnabled)
        assertFalse(providers.getValue("minimax").isEnabled)
    }

    @Test
    fun onlyOrdinaryModelsAreChatModels() {
        assertTrue(LLMModelItem("chat", "Chat").isChatModel)
        assertFalse(LLMModelItem("stt", "STT", isStt = true).isChatModel)
        assertFalse(LLMModelItem("tts", "TTS", isTts = true).isChatModel)
    }

    @Test
    fun providerRequiresEnabledStateAndApiKeyForChat() {
        val configured = provider(isEnabled = true, apiKey = "key")

        assertTrue(configured.isConfiguredForChat)
        assertFalse(configured.copy(isEnabled = false).isConfiguredForChat)
        assertFalse(configured.copy(apiKey = "").isConfiguredForChat)
    }

    private fun provider(isEnabled: Boolean, apiKey: String) = LLMModelProvider(
        serviceId = "test",
        serviceName = "Test",
        isEnabled = isEnabled,
        apiKey = apiKey,
        apiBaseUrl = "https://example.com",
    )
}
