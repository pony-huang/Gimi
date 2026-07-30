package github.ponyhuang.gimi.agent.tools.mcp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parameters describing a local MCP server launched over stdio.
 *
 * Replaces the Java MCP SDK's `ServerParameters`: the Kotlin SDK's `StdioClientTransport` takes raw
 * streams, so the child process is spawned from this description by [DefaultMcpTransportBuilder].
 *
 * @property command The executable to run (e.g. `npx`, `python3`).
 * @property args Arguments passed to [command].
 * @property env Extra environment entries merged into the inherited environment.
 */
data class StdioServerParameters(
  val command: String,
  val args: List<String> = emptyList(),
  val env: Map<String, String> = emptyMap(),
)

/** Sealed class for holding MCP connection parameters. */
sealed class McpConnectionParameters {
  /**
   * Parameters for establishing a MCP Stdio connection.
   *
   * @property serverParameters Parameters for the MCP Stdio server.
   * @property timeoutDuration Timeout for establishing the connection to the MCP stdio server.
   */
  data class Stdio(
    val serverParameters: StdioServerParameters,
    val timeoutDuration: Duration = 5.seconds,
  ) : McpConnectionParameters()

  /**
   * Parameters for establishing a MCP Server-Sent Events (SSE) connection.
   *
   * @property url The URL of the SSE server.
   * @property sseEndpoint The SSE endpoint, appended to [url] unless blank.
   * @property headers The headers to include in the request.
   * @property timeout The connection timeout.
   * @property sseReadTimeout The SSE read timeout.
   */
  data class Sse(
    val url: String,
    val sseEndpoint: String = "sse",
    val headers: Map<String, String> = emptyMap(),
    val timeout: Duration = 5.seconds,
    val sseReadTimeout: Duration = 5.minutes,
  ) : McpConnectionParameters()

  /**
   * Parameters for establishing a MCP Streamable HTTP connection.
   *
   * @property url The URL of the HTTP server.
   * @property headers The headers to include in the request.
   * @property timeout The connection timeout.
   * @property readTimeout The read timeout.
   */
  data class StreamableHttp(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeout: Duration = 5.seconds,
    val readTimeout: Duration = 5.minutes,
  ) : McpConnectionParameters()
}
