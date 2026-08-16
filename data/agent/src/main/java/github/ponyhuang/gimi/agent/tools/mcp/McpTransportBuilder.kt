package github.ponyhuang.gimi.agent.tools.mcp

import io.modelcontextprotocol.spec.McpClientTransport

/** Builds an Android-compatible MCP client transport for one connection description. */
internal interface McpTransportBuilder {
  fun build(connectionParams: McpConnectionParameters): McpClientTransport
}
