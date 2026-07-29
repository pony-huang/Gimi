package github.ponyhuang.asssistantai.agent.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/**
 * A built MCP [Transport] together with the resources that back it.
 *
 * The Kotlin MCP SDK does not own the objects a transport is built from: an HTTP transport borrows
 * a Ktor `HttpClient` supplied by the caller and a stdio transport only reads/writes the streams of
 * a process the caller spawned. Closing the `Client` therefore closes the protocol, not those
 * resources, so every transport is handed out with the [release] hook that disposes of them.
 *
 * @property transport The transport to connect an MCP client with.
 * @property release Disposes of the resources backing [transport]. Must be idempotent-safe to call
 *   once after the client is closed (or after a failed initialization).
 */
internal class McpTransportHandle(val transport: Transport, val release: () -> Unit)

/**
 * Interface for building MCP client transports. Implementations of this interface are responsible
 * for constructing concrete [Transport] objects based on the provided connection parameters.
 */
internal interface McpTransportBuilder {
  /**
   * Builds a transport based on the provided connection parameters.
   *
   * @param connectionParams The parameters required to configure the transport. The type of this
   *   object determines the type of transport built.
   * @return A handle holding the transport and the hook releasing its backing resources.
   * @throws IllegalArgumentException if the connectionParams are not supported or invalid.
   */
  fun build(connectionParams: McpConnectionParameters): McpTransportHandle
}
