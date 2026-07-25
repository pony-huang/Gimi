package github.ponyhuang.adkagui

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.agent.model.Openai
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test


class KimiTest {

    @Test
    fun kimiModel() = runBlocking {
        val toolset = KimiFormulaToolset(
            PropertiesUtils.get("KIMI_API_KEY"),
            "https://api.moonshot.cn/v1",
            OkHttpClient()
        )
        val agent = LlmAgent(
            name = "Agent",
            toolsets = listOf(toolset),
            model = Kimi(),
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

    class Kimi : Openai(
        "kimi-k3", OpenAIOkHttpClient.builder()
            .baseUrl("https://api.moonshot.cn/v1")
            .apiKey(PropertiesUtils.get("KIMI_API_KEY"))
            .build()
    )
}