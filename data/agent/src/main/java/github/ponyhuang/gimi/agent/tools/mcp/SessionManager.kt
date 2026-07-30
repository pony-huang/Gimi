package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions

/**
 * An initialized MCP client together with the resources its transport was built from.
 *
 * `Client.close()` shuts the protocol down but leaves the Ktor `HttpClient` (or the stdio child
 * process) the transport borrowed running, so both are closed together here and a session -- not a
 * bare client -- is what the pool hands out.
 */
internal class McpSession(val client: Client, private val handle: McpTransportHandle) {
  /** Runs the MCP initialization handshake over this session's transport. */
  suspend fun connect() {
    client.connect(handle.transport)
  }

  /** Closes the client and releases its transport resources. Best-effort: never throws. */
  suspend fun close() {
    try {
      client.close()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to close MCP client: ${e.message}" }
    }
    try {
      handle.release()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to release MCP transport resources: ${e.message}" }
    }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(McpSession::class)
  }
}

/**
 * Owns and manages MCP client sessions.
 *
 * Implementations are the single owner of the sessions they hand out: sessions are pooled and
 * shared (so a stdio server is backed by exactly one child process), transparently replaced when
 * they die, and torn down wholesale on [close]. Callers ([github.ponyhuang.gimi.agent.tools.mcp.McpTool], [McpToolset]) hold a reference
 * to the manager rather than caching sessions themselves.
 */
internal interface SessionManager : AutoCloseable {
  /**
   * Returns an initialized session for the given [headers], creating and initializing one if none
   * is pooled yet. Sessions are keyed so that equivalent [headers] share a single session (a stdio
   * connection ignores headers and always shares one session).
   *
   * Pass the failed session as [stale] to recover from a dead one: if [stale] is still the pooled
   * session for these [headers] it is evicted, closed, and recreated; if another caller already
   * replaced it (dedup across tools sharing the session), the current pooled session is returned.
   * So the underlying client is created at most once per failure, and the common fetch is just
   * `getSession(headers)` with [stale] defaulting to `null`.
   */
  suspend fun getSession(
    headers: Map<String, String> = emptyMap(),
    stale: McpSession? = null,
  ): McpSession

  /**
   * Closes every session this manager created. Safe to call more than once.
   *
   * Being [AutoCloseable] allows `use {}`, and matches `MCPSessionManager.close` in ADK Python.
   */
  override fun close()

  /**
   * Whether any progress consumer is registered on the sessions this manager creates.
   *
   * MCP progress is opt-in per request: a server may only emit progress notifications that
   * reference a progress token the client supplied, so a request has to carry one for a consumer to
   * ever be called. [requestOptions] wires the callback (and therefore the token) only when this is
   * `true`, so servers are not asked to produce progress that nothing reads.
   */
  val hasProgressConsumers: Boolean

  /**
   * Per-request options for calls made on sessions from this manager: the request timeout derived
   * from the connection parameters, plus a progress callback fanning out to the registered
   * consumers when there are any. The SDK attaches the `_meta.progressToken` itself whenever a
   * progress callback is present.
   */
  fun requestOptions(): RequestOptions
}
