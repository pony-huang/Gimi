package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import io.modelcontextprotocol.client.McpAsyncClient
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull

/** One initialized Java SDK client; the client owns and closes its transport. */
internal class McpSession(val client: McpAsyncClient) {
  /** Runs the MCP initialization handshake. */
  suspend fun connect() {
    client.initialize().awaitSingleOrNull()
  }

  /** Closes protocol and transport resources. Best-effort and cancellation-aware. */
  suspend fun close() {
    try {
      client.closeGracefully().awaitSingleOrNull()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logger.warn(e) { "Failed to close MCP client: ${e.message}" }
    }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(McpSession::class)
  }
}

/** Owns pooled MCP sessions and request-scoped progress metadata. */
internal interface SessionManager : AutoCloseable {
  suspend fun getSession(
    headers: Map<String, String> = emptyMap(),
    stale: McpSession? = null,
  ): McpSession

  override fun close()

  val hasProgressConsumers: Boolean

  /** Returns `_meta` fields for a request, or null when progress is not observed. */
  fun requestMeta(): Map<String, Any>? = requestMeta(hasProgressConsumers)
}

/** Creates a unique MCP progress token only when a consumer can observe notifications. */
internal fun requestMeta(hasProgressConsumers: Boolean): Map<String, Any>? =
  if (hasProgressConsumers) mapOf("progressToken" to UUID.randomUUID().toString()) else null
