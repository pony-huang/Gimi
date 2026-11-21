package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import github.ponyhuang.gimi.domain.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelaccess.ToolLoadingMode
import github.ponyhuang.gimi.domain.modelaccess.isOfficialDeepSeekAnthropicEndpoint
import github.ponyhuang.gimi.domain.modelaccess.toolLoadingMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolLoadingModeTest {
    @Test
    fun onlyExplicitDeepSeekModelsAndOfficialEndpointsUseReferences() {
        val runtime = ModelRuntimeMetadata("instance-deepseek", "deepseek", ApiProtocol.Anthropic,
            "deepseek-flash", "https://api.deepseek.com/anthropic")
        assertEquals(ToolLoadingMode.DEEPSEEK_REFERENCE, toolLoadingMode(runtime))
        for (other in listOf(null, runtime.copy(templateId = "minimax"),
            runtime.copy(templateId = null),
            runtime.copy(modelId = "deepseek-v4-pro"), runtime.copy(modelId = "future-model"), runtime.copy(baseType = ApiProtocol.OpenaiChatcompletions),
            runtime.copy(fullBaseUrl = "https://proxy.example/anthropic"),
            runtime.copy(fullBaseUrl = "https://api.deepseek.com.evil.test/anthropic"))) {
            assertEquals(ToolLoadingMode.RESULT, toolLoadingMode(other))
        }
    }
}

class DeepSeekMessageEndpointTest {
    private val runtime = ModelRuntimeMetadata(
        "instance-deepseek", "deepseek", ApiProtocol.Anthropic,
        "deepseek-flash", "https://api.deepseek.com/anthropic",
    )

    @Test
    fun officialEndpointIsRecognizedInCanonicalAndVersionedForms() {
        for (base in listOf("https://api.deepseek.com/anthropic", "https://api.deepseek.com/anthropic/v1",
            "https://api.deepseek.com/anthropic/")) {
            assertTrue(isOfficialDeepSeekAnthropicEndpoint(runtime.copy(fullBaseUrl = base)))
        }
    }

    @Test
    fun proxiesOtherTemplatesAndProtocolsAreNotOfficial() {
        for (other in listOf(null, runtime.copy(templateId = "minimax"), runtime.copy(templateId = null),
            runtime.copy(baseType = ApiProtocol.OpenaiChatcompletions),
            runtime.copy(fullBaseUrl = "http://api.deepseek.com/anthropic"),
            runtime.copy(fullBaseUrl = "https://api.deepseek.com.evil.test/anthropic"),
            runtime.copy(fullBaseUrl = "https://proxy.example.com/anthropic"),
            runtime.copy(fullBaseUrl = "https://api.deepseek.com/anthropic?x=1"))) {
            assertFalse(isOfficialDeepSeekAnthropicEndpoint(other))
        }
    }
}
