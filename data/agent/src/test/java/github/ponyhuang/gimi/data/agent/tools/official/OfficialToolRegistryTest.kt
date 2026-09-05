package github.ponyhuang.gimi.data.agent.tools.official

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方工具注册表门控行为:服务/协议/模型家族三维匹配与厂商 wire 声明推导。
 */
class OfficialToolRegistryTest {

    private val registry = OfficialToolRegistry(
        kimiFormulaCache = testKimiFormulaCache(),
        httpClient = testHttpClient(),
        modelServices = emptyServices(),
    )

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
    fun providerWireNamesAreScopedByServiceAndProtocol() {
        assertEquals(
            setOf("web_search"),
            registry.providerDeclaredWireNames("openai", ApiProtocol.Standard, "gpt-5.2"),
        )
        assertEquals(
            setOf("web_search"),
            registry.providerDeclaredWireNames("mimo", ApiProtocol.Standard, "mimo-model"),
        )
        assertEquals(
            setOf("web_search"),
            registry.providerDeclaredWireNames("minimax", ApiProtocol.Anthropic, "minimax-model"),
        )
        // GLM 的本地搜索是可执行函数,不得转换为厂商 wire 形态。
        assertTrue(
            registry.providerDeclaredWireNames("glm", ApiProtocol.Standard, "glm-4.6").isEmpty(),
        )
        // 协议不匹配的服务没有 wire 声明。
        assertTrue(
            registry.providerDeclaredWireNames("openai", ApiProtocol.Anthropic, "gpt-5.2").isEmpty(),
        )
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
            registry.specsFor("glm", ApiProtocol.Standard, "glm-4.6").map { it.toolId },
        )
        assertEquals(
            listOf("glm_web_search"),
            registry.specsFor("glm", ApiProtocol.Anthropic, "glm-4.7").map { it.toolId },
        )
    }

    @Test
    fun similarModelNamesDoNotMatchGlmFamily() {
        // glmatrix、前缀分隔符之外的组合都不应误判为 GLM 家族。
        assertTrue(registry.specsFor("glm", ApiProtocol.Standard, "glmatrix").isEmpty())
        assertTrue(registry.specsFor("glm", ApiProtocol.Standard, "other-model").isEmpty())
    }

    @Test
    fun pathPrefixedModelIdsStillMatchFamily() {
        // 带厂商路径前缀的模型 ID(如 Gemini 风格 models/glm-4.6)取最后一段匹配。
        assertEquals(
            listOf("glm_web_search"),
            registry.specsFor("glm", ApiProtocol.Standard, "models/glm-4.6").map { it.toolId },
        )
    }

    @Test
    fun kimiMatchesBothFamilySpellingsAndProtocols() {
        assertEquals(
            listOf("kimi_formulas"),
            registry.specsFor("kimi", ApiProtocol.Standard, "kimi-k2.5").map { it.toolId },
        )
        assertEquals(
            listOf("kimi_formulas"),
            registry.specsFor("kimi", ApiProtocol.Anthropic, "moonshot-v1").map { it.toolId },
        )
        assertFalse(registry.specsFor("kimi", ApiProtocol.Gemini, "kimi-k2.5").any { it.toolId == "kimi_formulas" })
    }
}
