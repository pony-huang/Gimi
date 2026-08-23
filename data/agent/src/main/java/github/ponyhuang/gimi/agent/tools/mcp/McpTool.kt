package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.agent.tools.mcp.McpSchemaConverter.toAdkFunctionDeclaration
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolDeclarationException
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.Tool as McpSchemaTool
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Turns an MCP Tool into an ADK [BaseTool].
 *
 * The tool holds no session of its own: it fetches the shared session from [mcpSessionManager] on
 * each call and, on failure, asks the manager to reinitialize it. Because the manager owns the
 * session pool, a reinit is seen by every tool sharing the session and the toolset can close them
 * all via [github.ponyhuang.gimi.agent.tools.mcp.SessionManager.close].
 */
class McpTool
internal constructor(
  name: String,
  description: String,
  private val mcpSchemaTool: McpSchemaTool,
  private val mcpSessionManager: SessionManager,
  private val headers: Map<String, String> = emptyMap(),
) : BaseTool(name, description) {

  override fun declaration(): FunctionDeclaration? {
    try {
      return mcpSchemaTool.toAdkFunctionDeclaration()
    } catch (e: RuntimeException) {
      throw McpToolDeclarationException(
        "MCP tool:$name failed to get declaration, inputSchema:${mcpSchemaTool.inputSchema()}. outputSchema: ${mcpSchemaTool.outputSchema()}",
        e,
      )
    }
  }

  /**
   * Calls the MCP tool.
   *
   * Progress reporting is opt-in by the client: a server may only emit progress notifications that
   * reference a progress token supplied on the originating request, so without one a
   * spec-conformant server stays silent and the progress consumers registered on the session are
   * never called. The SDK attaches that token itself whenever the request options carry a progress
   * callback, which [SessionManager.requestOptions] wires up only when a consumer is listening --
   * so servers are not asked to produce progress that nothing reads.
   */
  override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
    val callResult = retrySessionCall {
      client.callTool(CallToolRequest(name, args, mcpSessionManager.requestMeta())).awaitSingle()
    }
    return callResult.toJsonNativeMap()
  }

  private suspend fun <T> retrySessionCall(
    times: Int = 4,
    delayMs: Long = 100,
    block: suspend McpSession.() -> T,
  ): T {
    var session: McpSession? = null
    for (i in 1 until times) {
      // First pass: stale=null (plain fetch). Later passes: name the failed session so the manager
      // replaces it in place, shared by every tool on that session.
      session = mcpSessionManager.getSession(headers, stale = session)
      try {
        return session.block()
      } catch (e: Exception) {
        if (e is CancellationException) {
          throw e
        }
        delay(delayMs)
        logger.warn(e) { "Retrying callTool due to: ${e.message}" }
      }
    }
    return mcpSessionManager.getSession(headers, stale = session).block()
  }

  internal val annotations: ToolAnnotations?
    get() = mcpSchemaTool.annotations()

  internal val meta: Map<String, Any>?
    get() = mcpSchemaTool.meta()

  companion object {
    private val logger = LoggerFactory.getLogger(McpTool::class)
  }
}

/**
 * Converts the foreign MCP SDK [CallToolResult] into a JSON-native map (only [Map], [List], String,
 * number, Boolean, or null). ADK wraps any non-[Map] tool result as `{"result": <value>}`; left as
 * the raw SDK object that payload is opaque to the model and, because it is not JSON-native, throws
 * when a serializing [com.google.adk.kt.sessions.SessionService] persists the event.
 *
 * Mirrors Python ADK's `CallToolResult.model_dump(exclude_none=True, mode="json")`: it goes through
 * the SDK's own Jackson mapper, so polymorphic
 * `content` entries keep their `type` discriminator and absent fields are dropped, matching the
 * result's canonical on-the-wire JSON.
 */
private fun CallToolResult.toJsonNativeMap(): Map<String, Any?> {
  @Suppress("UNCHECKED_CAST")
  return Json.parseToJsonElement(McpJsonDefaults.getMapper().writeValueAsString(this)).toJsonNative()
    as Map<String, Any?>
}

/** Recursively unwraps a [JsonElement] into plain Kotlin collections and primitives. */
private fun JsonElement.toJsonNative(): Any? =
  when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { (_, value) -> value.toJsonNative() }
    is JsonArray -> map { it.toJsonNative() }
    is JsonPrimitive ->
      when {
        isString -> content
        else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
      }
  }
