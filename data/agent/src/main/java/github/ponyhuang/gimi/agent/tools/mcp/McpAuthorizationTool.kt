package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.mcp.usecase.UpdateMcpAuthorizationForConversationUseCase
import javax.inject.Inject
import javax.inject.Singleton

/** 为当前会话最近等待凭据的 MCP server 补充 Authorization，且不向模型回显凭据。 */
@Singleton
class McpAuthorizationTool @Inject constructor(
    private val updateAuthorization: UpdateMcpAuthorizationForConversationUseCase,
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
                ARG_AUTHORIZATION to Schema(
                    type = Type.STRING,
                    description = "The token, Bearer value, or complete Authorization value supplied by the user.",
                ),
            ),
            required = listOf(ARG_AUTHORIZATION),
        ),
    )

    override suspend fun run(
        context: ToolContext,
        args: Map<String, Any?>,
    ): Any {
        val authorization = args[ARG_AUTHORIZATION] as? String
        if (authorization.isNullOrBlank()) return failure("Authorization must not be empty.")
        val sessionId = context.context.session.key.id
        if (sessionId.isNullOrBlank()) return failure("The current conversation has no persistent session id.")

        val result = updateAuthorization(sessionId, authorization)
        return buildMap {
            put("success", result.updated && result.error == null)
            result.serverName?.let { put("server", it) }
            result.error?.let { put("error", it) }
        }
    }

    private fun failure(message: String): Map<String, Any> = mapOf(
        "success" to false,
        "error" to message,
    )

    companion object {
        const val NAME: String = "update_mcp_server_authorization"
        private const val ARG_AUTHORIZATION: String = "authorization"
        private const val DESCRIPTION: String =
            "Updates Authorization for the current conversation's most recently imported MCP " +
                "server that is waiting for credentials. Never use it for unrelated secrets."
    }
}
