package github.ponyhuang.adkagui

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import github.ponyhuang.asssistantai.agent.model.Claude
import kotlinx.coroutines.runBlocking
import org.junit.Test


class MinimaxTest {

    @Test
    fun kimiModel() = runBlocking {
        val agent = LlmAgent(
            name = "Agent",
            model = Minimax(),
            maxSteps = 3,
            generateContentConfig = GenerateContentConfig()
        )
        InMemoryRunner(agent).run(
            userId = "user123",
            sessionId = "sessionId123",
            Content.fromText(
                role = "user",
                text = "请搜索 \"一方感恩碑 廿载育人心\"，并告诉我它是什么。"
            ),
//            runConfig = RunConfig(StreamingMode.SSE)
        ).forEach {
            println(it)
        }
    }

    class Minimax : Claude(
        "MiniMax-M3", AnthropicOkHttpClient.builder()
            .baseUrl("https://api.minimaxi.com/anthropic")
            .apiKey(PropertiesUtils.get("MINIMAX_API_KEY"))
            .build()
    )
}