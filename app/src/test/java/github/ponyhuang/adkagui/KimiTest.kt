package github.ponyhuang.adkagui

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Tool
import org.junit.Test

class KimiTest {

    @Test
    fun kimiModel() {
        val agent = LlmAgent(
            name = "Agent",
            tools = listOf(WebSearchTool()),
            model = PropertiesUtils.kimiForCodingModel(),
            generateContentConfig = GenerateContentConfig()
        )
        InMemoryRunner(agent).run(
            userId = "user123",
            sessionId = "sessionId123",
            Content.fromText(
                role = "user",
                text = "请搜索 Moonshot AI Context Caching 技术，并告诉我它是什么。"
            )
        ).forEach {
            println(it)
        }
    }

    class WebSearchTool : BaseTool(name = "$" + "web_search", description = "$" + "web_search") {

        override fun declaration(): FunctionDeclaration = FunctionDeclaration(
            name = name,
            description = description
        )

        override suspend fun run(context: ToolContext, args: Map<String, Any>): Any {
            throw UnsupportedOperationException("WebSearchTool does not support local execution")
        }

        override suspend fun processLlmRequest(
            toolContext: ToolContext,
            llmRequest: LlmRequest,
        ): LlmRequest {
            val config = llmRequest.config
            val existingTools = config.tools?.toMutableList() ?: mutableListOf()

            val hasSearchTool = existingTools.any { tool ->
                tool.functionDeclarations?.any { it.name == name }
                    ?: false
            }
            if (hasSearchTool) {
                return llmRequest
            }

            existingTools.add(Tool(functionDeclarations = listOf(declaration())))
            return llmRequest.copy(config = config.copy(tools = existingTools))
        }
    }
}