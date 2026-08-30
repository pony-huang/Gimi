package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool

/**
 * Selects which of an MCP server's tools are exposed to the agent.
 *
 * ADK Kotlin does not ship a tool filter of its own, so this mirrors the filter of ADK Python's
 * `MCPToolset(tool_filter=...)`: either a static list of tool names or a context-aware predicate.
 */
sealed class ToolFilter {
  /** Keeps only the tools whose name is in [names]. */
  data class AllowList(val names: Set<String>) : ToolFilter()

  /** Keeps the tools for which [predicate] returns `true`. */
  class Predicate(val predicate: (BaseTool, ReadonlyContext?) -> Boolean) : ToolFilter()

  companion object {
    /** Creates an [AllowList] filter from the given tool [names]. */
    fun allowList(vararg names: String): AllowList = AllowList(names.toSet())
  }
}

/** Whether [tool] passes this filter. A `null` filter selects every tool. */
fun ToolFilter?.isToolSelected(tool: BaseTool, readonlyContext: ReadonlyContext?): Boolean =
  when (this) {
    null -> true
    is ToolFilter.AllowList -> tool.name in names
    is ToolFilter.Predicate -> predicate(tool, readonlyContext)
  }
