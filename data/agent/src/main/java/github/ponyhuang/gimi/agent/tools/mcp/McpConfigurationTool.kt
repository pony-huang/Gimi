package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.usecase.ImportMcpServersForConversationUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 允许 Agent 把用户明确提供的 MCP JSON 导入全局配置并绑定到当前会话。
 *
 * 返回值只包含计数和服务器名称，避免把 URL、请求头或 token 再次暴露给模型。
 */
@Singleton
class McpConfigurationTool @Inject constructor(
    private val importForConversation: ImportMcpServersForConversationUseCase,
    private val repository: McpRepository,
) : BaseTool(
    name = NAME,
    description = DESCRIPTION,
) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                ARG_CONFIG_JSON to Schema(
                    type = Type.STRING,
                    description = "The complete raw MCP JSON configuration provided by the user.",
                ),
            ),
            required = listOf(ARG_CONFIG_JSON),
        ),
    )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = super.processLlmRequest(toolContext, llmRequest)
        .appendInstructions(
            Content(parts = listOf(Part(text = MCP_IMPORT_INSTRUCTION))),
        )

    override suspend fun run(
        context: ToolContext,
        args: Map<String, Any?>,
    ): Any {
        val json = args[ARG_CONFIG_JSON] as? String
        if (json.isNullOrBlank()) {
            return failure("config_json must contain an MCP JSON configuration.")
        }
        val sessionId = context.context.session.key.id
        if (sessionId.isNullOrBlank()) {
            return failure("The current conversation has no persistent session id.")
        }

        val result = importForConversation(sessionId, json)
        val imported = result.importResult
        val namesById = repository.currentServers().associate { it.id to it.name }
        val serverNames = imported.affectedServerIds.mapNotNull(namesById::get)
        return buildMap {
            put(
                "success",
                imported.error == null && imported.imported > 0 && result.conversationActivated,
            )
            put("created", imported.created)
            put("updated", imported.updated)
            put("skipped", imported.skipped)
            put("conversation_activated", result.conversationActivated)
            put("servers", serverNames)
            imported.error?.let { put("error", it) }
            if (imported.error == null && imported.imported > 0 && !result.conversationActivated) {
                put("error", "MCP servers were imported but could not be enabled for this conversation.")
            }
        }
    }

    private fun failure(message: String): Map<String, Any> = mapOf(
        "success" to false,
        "created" to 0,
        "updated" to 0,
        "skipped" to 0,
        "conversation_activated" to false,
        "servers" to emptyList<String>(),
        "error" to message,
    )

    companion object {
        const val NAME: String = "import_mcp_servers"
        private const val ARG_CONFIG_JSON: String = "config_json"
        private const val DESCRIPTION: String =
            "Imports or updates remote MCP server configurations from JSON explicitly pasted " +
                "by the user and enables them for the current conversation. Call this only when " +
                "the content is clearly an MCP configuration. Supports SSE and Streamable HTTP; " +
                "stdio entries are skipped."
        private const val MCP_IMPORT_INSTRUCTION: String =
            "When a user message clearly contains an MCP server configuration, call " +
                "import_mcp_servers with the complete configuration JSON. Do not call it for " +
                "unrelated JSON, and do not invent missing MCP fields. Sending a clear MCP " +
                "configuration is authorization for that import, so do not ask for an " +
                "additional confirmation."
    }
}
