package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 独立于 Android、网络和 Agent SDK 的官方工具可用性规则。 */
class OfficialToolSupportTest {
    private val support = OfficialToolSupport()

    @Test
    fun modelFamilyMatchingRequiresASeparatorAndIgnoresPathAndCase() {
        for (modelId in listOf("GLM", "models/GLM-4.6", "glm_4", "glm.4")) {
            assertEquals(
                listOf(OfficialToolIds.GLM_WEB_SEARCH),
                support.specsFor("glm", ApiProtocol.Standard, modelId).map { it.toolId },
            )
        }
        assertTrue(support.specsFor("glm", ApiProtocol.Standard, "glmatrix").isEmpty())
    }

    @Test
    fun crossModelAvailabilityIncludesOnlyIndependentApiTools() {
        val specs = available(listOf(service("openai"), service("minimax"), service("kimi")))
        assertEquals(
            listOf(OfficialToolIds.OPENAI_WEB_SEARCH, OfficialToolIds.MINIMAX_IMAGE_GENERATION),
            specs.map { it.toolId },
        )
        assertFalse(specs.any { it.toolId == OfficialToolIds.MINIMAX_WEB_SEARCH })
    }

    @Test
    fun disabledOrUncredentialedSourcesCannotContributeTools() {
        for (source in listOf(
            service("minimax").copy(isEnabled = false),
            service("minimax").copy(isOfficialToolsEnabled = false),
            service("minimax").copy(apiKey = " "),
        )) {
            assertTrue(available(listOf(source)).isEmpty())
        }
    }

    @Test
    fun crossModelToolsRequireSupportedSourceProtocol() {
        assertTrue(available(listOf(service("minimax").copy(apiProtocol = ApiProtocol.Gemini))).isEmpty())
    }

    @Test
    fun absentActiveServiceCanStillReceiveIndependentApiTools() {
        assertEquals(
            listOf(OfficialToolIds.MINIMAX_IMAGE_GENERATION),
            available(listOf(service("minimax"))).map { it.toolId },
        )
    }

    private fun available(services: List<LLMModelSetting>) = support.availableSpecsFor(
        "openai", ApiProtocol.Standard, "gpt-5.2", services,
    )

    private fun service(id: String) = LLMModelSetting(
        id = id, name = id, isEnabled = true, apiKey = "key",
        apiBaseUrl = "https://example.com", apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://example.com", groups = emptyList(),
    )
}
