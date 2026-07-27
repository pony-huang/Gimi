package github.ponyhuang.asssistantai.data

import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAvailabilityTest {

    @Test
    fun builtInDeepSeekAndMiniMaxStartDisabled() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        assertFalse(providers.getValue("deepseek").isEnabled)
        assertFalse(providers.getValue("minimax").isEnabled)
    }

    @Test
    fun builtInOpenAiAnthropicAndKimiExistWithProtocolConstraints() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val openAi = providers.getValue("openai")
        assertFalse(openAi.isEnabled)
        assertEquals(listOf(ApiBaseType.Standard), openAi.supportedBaseTypes)
        assertEquals(ApiBaseType.Standard, openAi.baseType)

        val anthropic = providers.getValue("anthropic")
        assertFalse(anthropic.isEnabled)
        assertEquals(listOf(ApiBaseType.Anthropic), anthropic.supportedBaseTypes)
        assertEquals(ApiBaseType.Anthropic, anthropic.baseType)

        val kimi = providers.getValue("kimi")
        assertFalse(kimi.isEnabled)
        assertTrue(ApiBaseType.Standard in kimi.supportedBaseTypes)
        assertTrue(ApiBaseType.Anthropic in kimi.supportedBaseTypes)
    }

    @Test
    fun builtInProvidersDeclareTheirOfficialToolIntegrations() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val miniMax = providers.getValue("minimax")
        assertTrue(miniMax.supportedOfficialTools.isEmpty())
        assertEquals(
            listOf(OfficialToolIds.WEB_SEARCH),
            miniMax.copy(baseType = ApiBaseType.Anthropic).supportedOfficialTools,
        )
        assertEquals(
            listOf(OfficialToolIds.WEB_SEARCH),
            providers.getValue("mimo").supportedOfficialTools,
        )
        assertEquals(
            listOf(OfficialToolIds.KIMI_FORMULAS),
            providers.getValue("kimi").supportedOfficialTools,
        )
        assertTrue(
            providers.getValue("mimo")
                .copy(baseType = ApiBaseType.Anthropic)
                .supportedOfficialTools
                .isEmpty(),
        )
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
