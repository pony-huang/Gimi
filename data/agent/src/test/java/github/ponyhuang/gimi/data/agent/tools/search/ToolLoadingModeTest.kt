package github.ponyhuang.gimi.data.agent.tools.search

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolLoadingModeTest {
    @Test
    fun onlyExplicitDeepSeekModelsAndOfficialEndpointsUseReferences() {
        val runtime = ModelRuntimeMetadata("deepseek", ApiProtocol.Anthropic,
            "deepseek-flash", "https://api.deepseek.com/anthropic")
        assertEquals(ToolLoadingMode.DEEPSEEK_REFERENCE, toolLoadingMode(runtime))
        for (other in listOf(null, runtime.copy(serviceId = "minimax"),
            runtime.copy(modelId = "deepseek-v4-pro"), runtime.copy(modelId = "future-model"), runtime.copy(baseType = ApiProtocol.OpenaiChatcompletions),
            runtime.copy(fullBaseUrl = "https://proxy.example/anthropic"),
            runtime.copy(fullBaseUrl = "https://api.deepseek.com.evil.test/anthropic"))) {
            assertEquals(ToolLoadingMode.RESULT, toolLoadingMode(other))
        }
    }
}
