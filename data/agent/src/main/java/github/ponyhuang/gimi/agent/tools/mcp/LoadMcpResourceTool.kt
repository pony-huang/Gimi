package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolExecutionException
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.CancellationException

/**
 * A built-in tool that allows the ADK agents to load resources exposed by the MCP server.
 *
 * Requires `useMcpResources = true` in the `McpToolset` configuration.
 */
internal class LoadMcpResourceTool(
  private val mcpToolset: McpToolset,
  private val maxMcpResourceLength: Int,
) : BaseTool("load_mcp_resource", DESCRIPTION) {
  override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
    try {
      val given = ARGUMENT_KEYS.filter { args.containsKey(it) }
      if (given.size != 1) return wrongArgumentCountMessage(args, given)
      val key = given.single()
      val value = args[key] as? String ?: return notAStringMessage(key, args[key])

      val readonlyContext = context.context
      val uri =
        if (key == URI) {
          value
        } else {
          val resources = mcpToolset.listAllResources(readonlyContext)
          val matches = resources.filter { it.name == value }
          when (matches.size) {
            0 -> return resourceNotFoundMessage(value)
            1 -> matches.single().uri
            else -> return ambiguousNameMessage(value, matches)
          }
        }

      val contents =
        try {
          mcpToolset.readResource(uri, readonlyContext)
        } catch (e: McpException) {
          if (e.code != RPCError.ErrorCode.RESOURCE_NOT_FOUND) throw e
          logger.warn { "MCP server has no resource at uri \"$uri\"." }
          return uriNotFoundMessage(uri)
        }

      return contents.joinToString("\n\n") { render(it) }
    } catch (e: CancellationException) {
      throw e // Re-throw cancellation exceptions as they are not indicative of a tool failure.
    } catch (e: Exception) {
      throw McpToolExecutionException("Failed to load MCP resource: ${e.message}", cause = e)
    }
  }

  private fun render(content: McpResourceContent): String =
    when (content) {
      is McpResourceContent.Text ->
        if (content.text.length > maxMcpResourceLength) {
          content.text.take(maxMcpResourceLength) + "... [Content truncated due to size limit]"
        } else {
          content.text
        }
      is McpResourceContent.Blob ->
        "[Warning: Binary data found at this URI, cannot display raw content]"
    }

  private fun wrongArgumentCountMessage(args: Map<String, Any?>, given: List<String>): String {
    val problem =
      if (given.isEmpty()) {
        "neither was given"
      } else {
        val malformed = given.filter { args[it] !is String }
        when {
          malformed.isEmpty() -> "both were given"
          malformed.size == given.size -> "both were given, and neither is a string"
          else -> "both were given, and ${malformed.joinToString { "\"$it\"" }} is not a string"
        }
      }
    return "This tool takes exactly one of \"$NAME\" or \"$URI\", as a string, but $problem. " +
      "Use \"$NAME\" for a listed resource, or \"$URI\" to read a resource URI directly."
  }

  private fun notAStringMessage(key: String, value: Any?): String {
    val actual = value?.let { it::class.simpleName } ?: "null"
    return "The \"$key\" argument must be a string, but was $actual."
  }

  private fun uriNotFoundMessage(uri: String): String =
    "No resource at URI \"$uri\" on the MCP server. Check the URI, or call " +
      "list_mcp_resources to see what is available."

  private fun resourceNotFoundMessage(name: String): String =
    "No resource named \"$name\" is available on the MCP server. " +
      "Call list_mcp_resources to see the available resource names."

  private fun ambiguousNameMessage(name: String, matches: List<McpResourceInfo>): String {
    val candidates =
      matches.joinToString("\n") { resource ->
        buildString {
          append("- ").append(resource.uri)
          resource.description?.let { append(" - ").append(it) }
          resource.mimeType?.let { append(" [").append(it).append("]") }
        }
      }
    return "The name \"$name\" is ambiguous: ${matches.size} resources share it. " +
      "Call this tool again with one of these URIs:\n$candidates"
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
              NAME to
                Schema(
                  type = Type.STRING,
                  description =
                    "The resource name returned by list_mcp_resources. Provide exactly one of " +
                      "'name' or 'uri'.",
                ),
              URI to Schema(type = Type.STRING, description = "The URI of the resource to load.")
            ),
          required = emptyList(),
        ),
    )
  }

  companion object {
    private val logger = LoggerFactory.getLogger(LoadMcpResourceTool::class)

    private const val NAME = "name"
    private const val URI = "uri"
    private val ARGUMENT_KEYS = listOf(NAME, URI)

    private const val DESCRIPTION =
      "Load a resource from the MCP server. Provide exactly one of 'uri' or 'name'; prefer 'uri' " +
        "when available. Text is truncated at the configured limit and binary content is " +
        "represented by a short warning."
  }
}
