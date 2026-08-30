package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import kotlinx.coroutines.CancellationException

/** One initialized Kotlin SDK client and the module-owned resources backing its transport. */
internal class McpSession(
  val client: Client,
  private val transportHandle: McpTransportHandle,
) {
  /** Connects the client and performs the MCP initialization handshake. */
  suspend fun connect() {
    client.connect(transportHandle.transport)
  }

  /** Closes protocol, transport, and backing resources. Best-effort and cancellation-aware. */
  suspend fun close() {
    try {
      client.close()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      logger.warn(error) { "Failed to close MCP client: ${error.message}" }
    } finally {
      transportHandle.release()
    }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(McpSession::class)
  }
}

/** Owns pooled MCP sessions and request-scoped progress callbacks. */
internal interface SessionManager : AutoCloseable {
  suspend fun getSession(
    headers: Map<String, String> = emptyMap(),
    stale: McpSession? = null,
  ): McpSession

  override fun close()

  val hasProgressConsumers: Boolean

  fun requestOptions(): RequestOptions
}
