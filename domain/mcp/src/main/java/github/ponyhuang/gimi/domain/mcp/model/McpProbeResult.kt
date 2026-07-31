package github.ponyhuang.gimi.domain.mcp.model

data class McpToolSummary(
    val name: String,
    val description: String = "",
)

/**
 * MCP 服务器连通性探测结果。[reachable] 为 false 时通过 [errorMessage] 给出可读原因。
 */
data class McpProbeResult(
    val reachable: Boolean,
    val serverName: String? = null,
    val serverVersion: String? = null,
    val tools: List<McpToolSummary> = emptyList(),
    val resources: List<String> = emptyList(),
    val prompts: List<String> = emptyList(),
    val errorMessage: String? = null,
)
