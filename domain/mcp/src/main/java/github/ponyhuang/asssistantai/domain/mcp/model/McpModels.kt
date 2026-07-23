package github.ponyhuang.asssistantai.domain.mcp.model

import java.util.UUID

enum class McpTransport {
    SSE,
    STREAMABLE_HTTP,
}

data class McpServer(
    val id: String = UUID.randomUUID().toString(),
    // 新建时留空，由编辑器 placeholder 引导用户命名；避免所有服务器同叫一个默认名。
    val name: String = "",
    val description: String = "",
    val endpointUrl: String = "",
    val transport: McpTransport = McpTransport.STREAMABLE_HTTP,
    val bearerToken: String = "",
    val headers: String = "",
    val isEnabled: Boolean = true,
)

data class McpImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val message: String
        get() = error ?: buildString {
            append("已导入 $imported 个 MCP 服务")
            if (skipped > 0) append("；跳过 $skipped 个不受支持的 stdio 或无效配置")
        }
}
