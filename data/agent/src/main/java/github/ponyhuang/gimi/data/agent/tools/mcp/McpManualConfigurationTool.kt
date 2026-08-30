package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.mcp.usecase.ConfigureMcpServerForConversationUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 允许 Agent 手动配置单个 MCP server（名称、端点、传输、认证、请求头）并同步当前会话选择。
 *
 * 与 [McpConfigurationTool] 的批量导入互补：用户用自然语言描述服务器字段而非粘贴
 * 完整 JSON/curl 时调用本工具。返回值只包含服务器名称与状态标记，绝不回显端点 URL、
 * Bearer Token 或自定义请求头，避免把凭据再次暴露给模型。
 */
@Singleton
class McpManualConfigurationTool @Inject constructor(
    private val configureForConversation: ConfigureMcpServerForConversationUseCase,
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
                ARG_NAME to Schema(
                    type = Type.STRING,
                    description = "A short, unique display name for the MCP server. " +
                        "Reusing an existing name updates that server instead of creating a new one.",
                ),
                ARG_ENDPOINT_URL to Schema(
                    type = Type.STRING,
                    description = "The full http(s) endpoint URL of the MCP server.",
                ),
                ARG_TRANSPORT to Schema(
                    type = Type.STRING,
                    enum = listOf("sse", "streamable_http"),
                    description = "Transport protocol. Defaults to streamable_http.",
                ),
                ARG_DESCRIPTION to Schema(
                    type = Type.STRING,
                    description = "Optional human-readable description of the server.",
                ),
                ARG_BEARER_TOKEN to Schema(
                    type = Type.STRING,
                    description = "Optional Bearer token sent as the Authorization header.",
                ),
                ARG_HEADERS to Schema(
                    type = Type.STRING,
                    description = "Optional extra request headers, one per line as Name=Value. " +
                        "Do not put an Authorization header here; use bearer_token instead.",
                ),
                ARG_ENABLED to Schema(
                    type = Type.BOOLEAN,
                    description = "Whether the server should be enabled for the current " +
                        "conversation. Defaults to true.",
                ),
            ),
            required = listOf(ARG_NAME, ARG_ENDPOINT_URL),
        ),
    )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = super.processLlmRequest(toolContext, llmRequest)
        .appendInstructions(
            Content(parts = listOf(Part(text = MANUAL_CONFIG_INSTRUCTION))),
        )

    override suspend fun run(
        context: ToolContext,
        args: Map<String, Any?>,
    ): Any {
        val name = args[ARG_NAME] as? String
        val endpointUrl = args[ARG_ENDPOINT_URL] as? String
        if (name.isNullOrBlank() || endpointUrl.isNullOrBlank()) {
            return failure("name and endpoint_url are required.")
        }
        val transport = parseTransport(args[ARG_TRANSPORT] as? String)
            ?: return failure("transport must be \"sse\" or \"streamable_http\".")
        val sessionId = context.context.session.key.id
        if (sessionId.isNullOrBlank()) {
            return failure("The current conversation has no persistent session id.")
        }

        val enabled = args[ARG_ENABLED] as? Boolean ?: true
        val result = configureForConversation(
            sessionId = sessionId,
            name = name,
            endpointUrl = endpointUrl,
            transport = transport,
            description = args[ARG_DESCRIPTION] as? String ?: "",
            bearerToken = args[ARG_BEARER_TOKEN] as? String ?: "",
            headers = args[ARG_HEADERS] as? String ?: "",
            enabled = enabled,
        )
        // 会话选择必须落在请求的启用意图上才算成功：请求启用时需已加入会话，
        // 请求停用时需已移出会话。
        val success = result.configured &&
            result.error == null &&
            result.conversationActivated == enabled
        return buildMap {
            put("success", success)
            put("server", result.serverName)
            put("created", result.created)
            put("updated", result.updated)
            put("conversation_activated", result.conversationActivated)
            result.error?.let { put("error", it) }
            if (enabled && result.configured && result.error == null && !result.conversationActivated) {
                put(
                    "error",
                    "The MCP server was saved but could not be enabled for this conversation.",
                )
            }
        }
    }

    private fun parseTransport(value: String?): McpTransport? = when (value?.lowercase()) {
        "sse" -> McpTransport.SSE
        "streamable_http", "streamablehttp", null, "" -> McpTransport.STREAMABLE_HTTP
        else -> null
    }

    private fun failure(message: String): Map<String, Any> = mapOf(
        "success" to false,
        "server" to "",
        "created" to false,
        "updated" to false,
        "conversation_activated" to false,
        "error" to message,
    )

    companion object {
        const val NAME: String = "configure_mcp_server"
        private const val ARG_NAME: String = "name"
        private const val ARG_ENDPOINT_URL: String = "endpoint_url"
        private const val ARG_TRANSPORT: String = "transport"
        private const val ARG_DESCRIPTION: String = "description"
        private const val ARG_BEARER_TOKEN: String = "bearer_token"
        private const val ARG_HEADERS: String = "headers"
        private const val ARG_ENABLED: String = "enabled"
        private const val DESCRIPTION: String =
            "Manually configures a single remote MCP server from explicit fields " +
                "(name, endpoint URL, transport, optional bearer token and headers) and enables " +
                "it for the current conversation. Use this when the user describes an MCP " +
                "server's settings directly but does not provide a complete JSON or curl " +
                "configuration; import_mcp_servers remains the tool for full config content. " +
                "Supports SSE and Streamable HTTP. Never invent endpoint URLs, tokens, or headers."
        private const val MANUAL_CONFIG_INSTRUCTION: String =
            "When a user message clearly describes an MCP server to add or update using " +
                "individual details such as a name, endpoint URL, transport type, token, or " +
                "headers — but does not provide a complete mcpServers JSON or curl command — " +
                "call configure_mcp_server with exactly those fields and nothing invented. " +
                "Reusing an existing server name updates that server. If the tool reports " +
                "conversation_activated as false, tell the user the server was saved but is not " +
                "yet enabled for this conversation. Never repeat the bearer token, headers, or " +
                "endpoint URL back to the user, and never call this tool for unrelated secrets."
    }
}
