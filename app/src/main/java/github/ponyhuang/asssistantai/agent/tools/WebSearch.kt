package github.ponyhuang.asssistantai.agent.tools

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration

class WebSearchTool(
) : BaseTool(name = "web_search", description = "web_search") {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description
    )

    override suspend fun run(context: ToolContext, args: Map<String, Any>): Any {
        throw UnsupportedOperationException("WebSearchTool does not support local execution")
    }

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest
    ): LlmRequest {
        return super.processLlmRequest(toolContext, llmRequest)
    }
}
