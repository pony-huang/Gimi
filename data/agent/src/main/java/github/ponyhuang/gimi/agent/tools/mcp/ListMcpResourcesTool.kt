package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolExecutionException
import kotlinx.coroutines.CancellationException

/** A built-in tool that allows the ADK agents to list resources exposed by the MCP server. */
internal class ListMcpResourcesTool(
  private val mcpToolset: McpToolset,
) : BaseTool("list_mcp_resources", DESCRIPTION) {

  override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
    try {
      val cursor = args["cursor"] as? String

      val result = mcpToolset.listResources(cursor, context.context)

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
    private const val DESCRIPTION =
      "List resources available on the MCP server. Returns one page and an optional " +
        "'nextCursor'; pass it back as 'cursor' until no cursor remains. Use each entry's 'uri' " +
        "with load_mcp_resource to avoid resolving a non-unique name."

    private const val RESOURCE_NAME = "name"
    private const val RESOURCE_URI = "uri"
    private const val RESOURCE_DESCRIPTION = "description"
    private const val RESOURCE_MIME_TYPE = "mimeType"
  }
}
