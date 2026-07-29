package github.ponyhuang.asssistantai.agent.tools.mcp

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.asssistantai.agent.tools.mcp.McpToolException.McpToolExecutionException
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import kotlinx.coroutines.CancellationException

/** A built-in tool that allows the ADK agents to list resources exposed by the MCP server. */
internal class ListMcpResourcesTool(
  private val mcpSession: McpSession,
  private val mcpSessionManager: SessionManager,
) : BaseTool("list_mcp_resources", "List resources available on the MCP server.") {

  override suspend fun run(context: ToolContext, args: Map<String, Any>): Any {
    try {
      val cursor = args["cursor"] as? String

      val result =
        mcpSession.client.listResources(
          request = ListResourcesRequest(PaginatedRequestParams(cursor = cursor)),
          options = mcpSessionManager.requestOptions(),
        )

      val resources =
        result.resources.map { resource ->
          buildMap {
            put(RESOURCE_NAME, resource.name)
            put(RESOURCE_URI, resource.uri)
            resource.description?.let { description -> put(RESOURCE_DESCRIPTION, description) }
            resource.mimeType?.let { mimeType -> put(RESOURCE_MIME_TYPE, mimeType) }
          }
        }

      val response = mutableMapOf<String, Any>("resources" to resources)
      result.nextCursor?.let { response["nextCursor"] = it }
      return response
    } catch (e: CancellationException) {
      throw e // Re-throw cancellation exceptions as they are not indicative of a tool failure.
    } catch (e: Exception) {
      throw McpToolExecutionException("Failed to list MCP resources: ${e.message}", cause = e)
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
    const val RESOURCE_NAME = "name"
    const val RESOURCE_URI = "uri"
    const val RESOURCE_DESCRIPTION = "description"
    const val RESOURCE_MIME_TYPE = "mimeType"
  }
}
