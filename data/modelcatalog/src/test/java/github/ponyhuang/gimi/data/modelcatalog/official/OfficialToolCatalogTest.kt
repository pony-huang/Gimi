package github.ponyhuang.gimi.data.modelcatalog.official

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.KimiFormulaSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方工具目录门控行为:服务/协议/模型家族三维匹配与厂商 wire 声明推导。
 */
class OfficialToolCatalogTest {

    private val support = OfficialToolSupport()
    private val registry = catalogFor()

    @Test
    fun webSearchIsDeclaredPerVendorWithUniqueToolIds() {
        val wireToolIds = registry.all
            .filter { it.binding is OfficialToolBinding.ProviderDeclaration }
            .map { it.toolId }

        assertEquals(
            listOf("openai_web_search", "anthropic_web_search", "minimax_web_search", "mimo_web_search"),
            wireToolIds,
        )
    }

    @Test
    fun minimaxImageGenerationExposesTextAndImageFunctions() = runTest {
        assertEquals(
            listOf(
                OfficialToolIds.MINIMAX_TEXT_TO_IMAGE,
                OfficialToolIds.MINIMAX_IMAGE_TO_IMAGE,
            ),
            registry.listFunctions(OfficialToolIds.MINIMAX_IMAGE_GENERATION).map { it.id },
        )
    }

    @Test
    fun providerWireNamesAreScopedByServiceAndProtocol() {
        assertEquals(
            setOf("web_search"),
            registryFor("openai").providerDeclaredWireNames("openai", ApiProtocol.Standard, "gpt-5.2"),
        )
        assertEquals(
            setOf("web_search"),
            registryFor("mimo").providerDeclaredWireNames("mimo", ApiProtocol.Standard, "mimo-model"),
        )
        assertEquals(
            setOf("web_search"),
            registryFor("minimax").providerDeclaredWireNames("minimax", ApiProtocol.Anthropic, "minimax-model"),
        )
        // GLM 的本地搜索是可执行函数,不得转换为厂商 wire 形态。
        assertTrue(
            registryFor("glm").providerDeclaredWireNames("glm", ApiProtocol.Standard, "glm-4.6").isEmpty(),
        )
        // 协议不匹配的服务没有 wire 声明。
        assertTrue(
            registryFor("openai").providerDeclaredWireNames("openai", ApiProtocol.Anthropic, "gpt-5.2").isEmpty(),
        )
    }

    @Test
    fun providerWireNamesAreAbsentWhenTheSourceServiceIsMissing() {
        assertTrue(registry.providerDeclaredWireNames("openai", ApiProtocol.Standard, "gpt-5.2").isEmpty())
    }

    private fun registryFor(serviceId: String) = catalogFor(serviceId)

    @Test
    fun minimaxImageGenerationUsesLocalToolsForBothSupportedProtocols() {
        for (protocol in setOf(ApiProtocol.Standard, ApiProtocol.Anthropic)) {
            assertEquals(
                listOf(OfficialToolIds.MINIMAX_IMAGE_GENERATION),
                support.specsFor("minimax", protocol, "MiniMax-M2.7")
                    .filter { it.toolId == OfficialToolIds.MINIMAX_IMAGE_GENERATION }
                    .map { it.toolId },
            )
        }
    }

    @Test
    fun supportedToolIdsMatchServiceAndProtocolOnly() {
        assertEquals(
            setOf("gemini_web_search", "gemini_url_context", "gemini_google_maps"),
            registry.supportedToolIds("gemini", ApiProtocol.Gemini),
        )
        assertEquals(
            setOf("openai_web_search"),
            registry.supportedToolIds("openai", ApiProtocol.Standard),
        )
        assertTrue(registry.supportedToolIds("openai", ApiProtocol.Gemini).isEmpty())
        assertTrue(registry.supportedToolIds("unknown-service", ApiProtocol.Standard).isEmpty())
    }

    @Test
    fun glmSpecNarrowsByModelFamily() {
        assertEquals(
            listOf("glm_web_search"),
            support.specsFor("glm", ApiProtocol.Standard, "glm-4.6").map { it.toolId },
        )
        assertEquals(
            listOf("glm_web_search"),
            support.specsFor("glm", ApiProtocol.Anthropic, "glm-4.7").map { it.toolId },
        )
    }

    @Test
    fun similarModelNamesDoNotMatchGlmFamily() {
        // glmatrix、前缀分隔符之外的组合都不应误判为 GLM 家族。
        assertTrue(support.specsFor("glm", ApiProtocol.Standard, "glmatrix").isEmpty())
        assertTrue(support.specsFor("glm", ApiProtocol.Standard, "other-model").isEmpty())
    }

    @Test
    fun pathPrefixedModelIdsStillMatchFamily() {
        // 带厂商路径前缀的模型 ID(如 Gemini 风格 models/glm-4.6)取最后一段匹配。
        assertEquals(
            listOf("glm_web_search"),
            support.specsFor("glm", ApiProtocol.Standard, "models/glm-4.6").map { it.toolId },
        )
    }

    @Test
    fun kimiMatchesBothFamilySpellingsAndProtocols() {
        assertEquals(
            listOf("kimi_formulas"),
            support.specsFor("kimi", ApiProtocol.Standard, "kimi-k2.5").map { it.toolId },
        )
        assertEquals(
            listOf("kimi_formulas"),
            support.specsFor("kimi", ApiProtocol.Anthropic, "moonshot-v1").map { it.toolId },
        )
        assertFalse(support.specsFor("kimi", ApiProtocol.Gemini, "kimi-k2.5").any { it.toolId == "kimi_formulas" })
    }

    private fun catalogFor(serviceId: String? = null): DefaultOfficialToolFunctionCatalog {
        val services = if (serviceId == null) emptyList() else listOf(
            LLMModelSetting(
                id = serviceId, name = serviceId, isEnabled = true, apiKey = "key",
                apiBaseUrl = "https://example.com", apiProtocol = ApiProtocol.Standard,
                anthropicBaseUrl = "https://example.com", groups = emptyList(),
            ),
        )
        val source = mockk<AgentModelConfigurationSource> {
            every { currentServices() } returns services
        }
        return DefaultOfficialToolFunctionCatalog(support, source, mockk<KimiFormulaSource>())
    }
}
