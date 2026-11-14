package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import github.ponyhuang.gimi.data.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import org.junit.Assert.assertEquals
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
