package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle

/**
 * A built-in tool that allows the ADK agents to list resource templates exposed by the MCP server.
 */
internal class ListMcpResourceTemplatesTool(
  private val mcpSession: McpSession,
  private val mcpSessionManager: SessionManager,
) :
  BaseTool("list_mcp_resource_templates", "List resource templates available on the MCP server.") {

  override suspend fun run(context: ToolContext, args: Map<String, Any>): Any {
    try {
      val cursor = args["cursor"] as? String

      val meta = mcpSessionManager.requestMeta()
      val result =
        if (meta == null) mcpSession.client.listResourceTemplates(cursor).awaitSingle()
        else mcpSession.client.listResourceTemplates(cursor, meta).awaitSingle()

      val templates =
        result.resourceTemplates().map { template ->
          buildMap {
            put(TEMPLATE_NAME, template.name())
            put(TEMPLATE_URI_TEMPLATE, template.uriTemplate())
            template.description()?.let { description -> put(TEMPLATE_DESCRIPTION, description) }
            template.mimeType()?.let { mimeType -> put(TEMPLATE_MIME_TYPE, mimeType) }
          }
        }

      return buildMap<String, Any> {
        put("resourceTemplates", templates)
        result.nextCursor()?.let { put("nextCursor", it) }
      }
    } catch (e: CancellationException) {
      throw e // Re-throw cancellation exceptions as they are not indicative of a tool failure.
    } catch (e: Exception) {
      throw McpToolExecutionException(
        "Failed to list MCP resource templates: ${e.message}",
        cause = e,
      )
    }
  }

  override fun declaration(): FunctionDeclaration {
    return FunctionDeclaration(
      name = name,
      description = description,
      parameters =
        Schema(
          type = Type.OBJECT,
          properties =
            mapOf(
              "cursor" to
                Schema(
                  type = Type.STRING,
                  description = "Optional pagination cursor for listing the next page.",
                )
            ),
          required = emptyList(),
        ),
    )
  }

  companion object {
    const val TEMPLATE_NAME = "name"
    const val TEMPLATE_URI_TEMPLATE = "uriTemplate"
    const val TEMPLATE_DESCRIPTION = "description"
    const val TEMPLATE_MIME_TYPE = "mimeType"
  }
}
