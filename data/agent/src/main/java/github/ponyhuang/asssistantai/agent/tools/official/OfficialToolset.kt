package github.ponyhuang.asssistantai.agent.tools.official

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.ConfiguredModel
import github.ponyhuang.asssistantai.agent.ModelConfig

/**
 * Request-scoped official tools integrated through ADK's [Toolset] lifecycle.
 *
 * `LlmAgentTurn.prepareRequest` runs [processLlmRequest] after the request processors have attached
 * the current model. Each resolved [BaseTool] then processes that same request, which both appends
 * its declaration and records the executable instance in ADK's request-local tool map. The normal
 * [getTools] hook intentionally returns an empty list so ADK does not register the same tools twice.
 */
interface OfficialToolset : Toolset {

    /** Resolves the tools enabled for the immutable configuration carried by the request model. */
    suspend fun resolveTools(config: ModelConfig): List<BaseTool>

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val config = (llmRequest.model as? ConfiguredModel)?.modelConfig ?: return llmRequest
        return resolveTools(config).fold(llmRequest) { request, tool ->
            tool.processLlmRequest(toolContext, request)
        }
    }

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = emptyList()
}

/** Declaration-only tool executed by the remote model provider, never by the local Agent runtime. */
internal class OfficialBuiltInTool(
    toolId: String,
) : BaseTool(name = toolId, description = toolId) {

    override fun declaration(): FunctionDeclaration =
        FunctionDeclaration(name = name, description = description)

    override suspend fun run(context: ToolContext, args: Map<String, Any>): Any =
        throw UnsupportedOperationException("$name is executed by the model provider")
}
