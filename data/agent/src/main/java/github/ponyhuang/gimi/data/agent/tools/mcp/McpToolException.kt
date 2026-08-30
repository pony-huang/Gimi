package github.ponyhuang.gimi.data.agent.tools.mcp

/** Base exception for MCP tools. */
sealed class McpToolException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause) {
  /** Exception thrown when there's an error during MCP tool declaration generated. */
  class McpToolDeclarationException(message: String, cause: Throwable? = null) :
    McpToolException(message, cause)

  /** Exception thrown when there's an error during MCP tools loading/initialization. */
  class McpToolLoadingException(message: String, cause: Throwable? = null) :
    McpToolException(message, cause)

  /** Exception thrown when there's an error executing a built-in MCP tool. */
  class McpToolExecutionException(message: String, cause: Throwable? = null) :
    McpToolException(message, cause)
}
