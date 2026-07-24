package github.ponyhuang.asssistantai.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.agent.model.Claude
import github.ponyhuang.asssistantai.agent.model.Openai
import org.junit.Test

class ModeTest {

    private fun claudeModel() = Claude(
        "deepseek-v4-pro", AnthropicOkHttpClient.builder()
            .baseUrl("https://api.deepseek.com/anthropic")
            .apiKey("sk-b04ee4901e1a49d29464c48108a90519")
            .build()
    )

    private fun openaiModel() = Openai(
        "deepseek-v4-pro", OpenAIOkHttpClient.builder()
            .baseUrl("https://api.deepseek.com")
            .apiKey("sk-b04ee4901e1a49d29464c48108a90519")
            .build()
    )

    @Test
    fun testModels() {
        val claude = AnthropicOkHttpClient.builder()
            .baseUrl("https://api.minimaxi.com/anthropic")
            .apiKey("sk-cp-A1_85088fIckSkl-iaAqi-t0n0fhhRxE9LwKMkMjswhlOWOF5z9p7J9nwMC93rsoUI4ey7EJpNkYVZzcrIBrbLy-jSBEm-VqHPBTPpP7dnOUK5M4wLm_rXw")
            .build()
        claude.models().list().data().forEach {
            //ModelInfo{id=MiniMax-M3, capabilities=, createdAt=2026-06-01T00:00Z, displayName=MiniMax-M3, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.7, capabilities=, createdAt=2026-03-18T02:00Z, displayName=MiniMax-M2.7, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.7-highspeed, capabilities=, createdAt=2026-03-18T02:00Z, displayName=MiniMax-M2.7-Highspeed, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.5, capabilities=, createdAt=2026-02-13T02:00Z, displayName=MiniMax-M2.5, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.5-highspeed, capabilities=, createdAt=2026-02-13T02:00Z, displayName=MiniMax-M2.5-Highspeed, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.1, capabilities=, createdAt=2025-12-23T02:00Z, displayName=MiniMax-M2.1, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2.1-highspeed, capabilities=, createdAt=2025-12-23T02:00Z, displayName=MiniMax-M2.1-Highspeed, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            //ModelInfo{id=MiniMax-M2, capabilities=, createdAt=2025-10-27T02:00Z, displayName=MiniMax-M2, maxInputTokens=, maxTokens=, type=model, additionalProperties={}}
            println(it)
        }

        val openai = OpenAIOkHttpClient.builder()
            .baseUrl("https://api.minimaxi.com/v1")
            .apiKey("sk-cp-A1_85088fIckSkl-iaAqi-t0n0fhhRxE9LwKMkMjswhlOWOF5z9p7J9nwMC93rsoUI4ey7EJpNkYVZzcrIBrbLy-jSBEm-VqHPBTPpP7dnOUK5M4wLm_rXw")
            .build()
        openai.models().list().data().forEach {
            // Model{id=MiniMax-M3, created=1780272000, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.7, created=1773799200, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.7-highspeed, created=1773799200, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.5, created=1770948000, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.5-highspeed, created=1770948000, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.1, created=1766455200, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2.1-highspeed, created=1766455200, object_=model, ownedBy=minimax, additionalProperties={}}
            //Model{id=MiniMax-M2, created=1761530400, object_=model, ownedBy=minimax, additionalProperties={}}
            println(it)
        }

    }
}