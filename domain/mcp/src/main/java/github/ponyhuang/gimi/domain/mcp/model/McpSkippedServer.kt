package github.ponyhuang.gimi.domain.mcp.model

/**
 * 会话加载 MCP 工具时被判定不可达而跳过的服务器。
 */
data class McpSkippedServer(
    val serverId: String,
    val displayName: String,
    val reason: String? = null,
)
