package github.ponyhuang.gimi.data.modelcatalog

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
        assertEquals(DUAL_API_BASE_TYPES.toSet(), glm.supportedBaseTypes.toSet())
        assertEquals(ApiBaseType.Anthropic, glm.baseType)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/", glm.apiBaseUrl)
        assertEquals("https://open.bigmodel.cn/api/anthropic", glm.anthropicBaseUrl)
        assertEquals(
            listOf(GLM_WEB_SEARCH_TOOL_ID),
            glm.toDomain().supportedOfficialTools,
        )
        assertEquals(
            listOf(GLM_WEB_SEARCH_TOOL_ID),
            glm.copy(baseType = ApiBaseType.Standard).toDomain().supportedOfficialTools,
        )
    }

    @Test
    fun builtInProvidersDeclareTheirOfficialToolIntegrations() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val miniMax = providers.getValue(LLMModelType.MiniMax.serviceId)
        assertTrue(miniMax.toDomain().supportedOfficialTools.isEmpty())
        assertEquals(
            listOf(WEB_SEARCH_TOOL_ID),
            miniMax.copy(baseType = ApiBaseType.Anthropic).toDomain().supportedOfficialTools,
        )
        assertEquals(
            listOf(WEB_SEARCH_TOOL_ID),
            providers.getValue(LLMModelType.Mimo.serviceId).toDomain().supportedOfficialTools,
        )
        assertEquals(
            listOf(KIMI_FORMULAS_TOOL_ID),
            providers.getValue(LLMModelType.Moonshot.serviceId).toDomain().supportedOfficialTools,
        )
        assertTrue(
            providers.getValue(LLMModelType.Mimo.serviceId)
                .copy(baseType = ApiBaseType.Anthropic)
                .toDomain()
                .supportedOfficialTools
                .isEmpty(),
        )
        assertEquals(
            listOf(WEB_SEARCH_TOOL_ID),
            providers.getValue(LLMModelType.Anthropic.serviceId).toDomain().supportedOfficialTools,
        )
        assertFalse(
            providers.getValue(LLMModelType.OpenAI.serviceId)
                .toDomain()
                .supportedOfficialTools
                .isEmpty(),
        )
        assertFalse(
            providers.getValue(LLMModelType.OpenAI.serviceId)
                .toDomain()
                .supportedOfficialTools
                .isEmpty(),
        )
    }

    @Test
    fun builtInGeminiIsSingleProtocolAndStartsDisabled() {
        val providers = LLMModelConfigs.services.associateBy { it.serviceId }

        val gemini = providers.getValue(LLMModelType.Gemini.serviceId)
        assertFalse(gemini.isEnabled)
        assertEquals(listOf(ApiBaseType.Gemini), gemini.supportedBaseTypes)
        assertEquals(ApiBaseType.Gemini, gemini.baseType)
        assertEquals(
            listOf(WEB_SEARCH_TOOL_ID, URL_CONTEXT_TOOL_ID, GOOGLE_MAPS_TOOL_ID),
            gemini.toDomain().supportedOfficialTools,
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

private const val WEB_SEARCH_TOOL_ID: String = "web_search"
private const val KIMI_FORMULAS_TOOL_ID: String = "kimi_formulas"
private const val GLM_WEB_SEARCH_TOOL_ID: String = "glm_web_search"
private const val URL_CONTEXT_TOOL_ID: String = "url_context"
private const val GOOGLE_MAPS_TOOL_ID: String = "google_maps"
