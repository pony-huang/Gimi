package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户指定的默认协议能力边界，避免重新引入厂商、端点或模型白名单。 */
class OpenAiCompatibleCloudFileChatSupportTest {
    @Test fun everyChatCompletionsProviderAndModelDefaultsToPdfFileReferences() {
        val support = OpenAiCompatibleCloudFileChatSupport()
        for (provider in listOf("openai", "deepseek", "custom", "future-provider")) {
            assertTrue(support.supports(cloudService(provider, "https://custom.example/v1"), "unknown-model", "application/pdf"))
        }
        assertFalse(support.supports(cloudService().copy(apiProtocol = ApiProtocol.Anthropic), "model", "application/pdf"))
        assertFalse(support.supports(cloudService().copy(apiProtocol = ApiProtocol.Gemini), "model", "application/pdf"))
        assertFalse(support.supports(cloudService(), "model", "image/png"))
    }
}
