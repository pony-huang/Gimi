package github.ponyhuang.gimi.data.agent.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/** A Kotlin SDK transport together with resources owned by this module. */
internal class McpTransportHandle(
  val transport: Transport,
  val release: () -> Unit = {},
)

/** Builds an Android-compatible MCP client transport for one connection description. */
internal interface McpTransportBuilder {
  fun build(connectionParams: McpConnectionParameters): McpTransportHandle
}
