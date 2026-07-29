package github.ponyhuang.asssistantai.data.modelcatalog

import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAvailabilityTest {

    @Test
    fun builtInDeepSeekAndMiniMaxStartDisabled() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        assertFalse(providers.getValue(LLMModelType.DeepSeek.serviceId).isEnabled)
        assertFalse(providers.getValue(LLMModelType.MiniMax.serviceId).isEnabled)
    }

    @Test
    fun builtInOpenAiAnthropicAndKimiExistWithProtocolConstraints() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val openAi = providers.getValue(LLMModelType.OpenAI.serviceId)
        assertFalse(openAi.isEnabled)
        assertEquals(listOf(ApiBaseType.Standard), openAi.supportedBaseTypes)
        assertEquals(ApiBaseType.Standard, openAi.baseType)

        val anthropic = providers.getValue(LLMModelType.Anthropic.serviceId)
        assertFalse(anthropic.isEnabled)
        assertEquals(listOf(ApiBaseType.Anthropic), anthropic.supportedBaseTypes)
        assertEquals(ApiBaseType.Anthropic, anthropic.baseType)

        val kimi = providers.getValue(LLMModelType.Moonshot.serviceId)
        assertFalse(kimi.isEnabled)
        assertTrue(ApiBaseType.Standard in kimi.supportedBaseTypes)
        assertTrue(ApiBaseType.Anthropic in kimi.supportedBaseTypes)
    }

    @Test
    fun builtInGlmExposesBothOpenAiAndAnthropicEndpoints() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val glm = providers.getValue(LLMModelType.Glm.serviceId)
        assertFalse(glm.isEnabled)
        assertEquals(ApiBaseType.entries.toSet(), glm.supportedBaseTypes.toSet())
        assertEquals(ApiBaseType.Anthropic, glm.baseType)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/", glm.apiBaseUrl)
        assertEquals("https://open.bigmodel.cn/api/anthropic", glm.anthropicBaseUrl)
        assertEquals(
            listOf(OfficialToolIds.GLM_WEB_SEARCH),
            glm.supportedOfficialTools,
        )
        assertEquals(
            listOf(OfficialToolIds.GLM_WEB_SEARCH),
            glm.copy(baseType = ApiBaseType.Standard).supportedOfficialTools,
        )
    }

    @Test
    fun builtInProvidersDeclareTheirOfficialToolIntegrations() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val miniMax = providers.getValue(LLMModelType.MiniMax.serviceId)
        assertTrue(miniMax.supportedOfficialTools.isEmpty())
        assertEquals(
            listOf(OfficialToolIds.WEB_SEARCH),
            miniMax.copy(baseType = ApiBaseType.Anthropic).supportedOfficialTools,
        )
        assertEquals(
            listOf(OfficialToolIds.WEB_SEARCH),
            providers.getValue(LLMModelType.Mimo.serviceId).supportedOfficialTools,
        )
        assertEquals(
            listOf(OfficialToolIds.KIMI_FORMULAS),
            providers.getValue(LLMModelType.Moonshot.serviceId).supportedOfficialTools,
        )
        assertTrue(
            providers.getValue(LLMModelType.Mimo.serviceId)
                .copy(baseType = ApiBaseType.Anthropic)
                .supportedOfficialTools
                .isEmpty(),
        )
        assertEquals(
            listOf(OfficialToolIds.WEB_SEARCH),
            providers.getValue(LLMModelType.Anthropic.serviceId).supportedOfficialTools,
        )
        assertFalse(
            providers.getValue(LLMModelType.OpenAI.serviceId).supportedOfficialTools.isEmpty(),
        )
        assertFalse(
            providers.getValue(LLMModelType.OpenAI.serviceId).supportedOfficialTools.isEmpty(),
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
