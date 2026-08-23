package github.ponyhuang.gimi.domain.mcp.model

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

/**
 * MCP 配置批量导入结果。
 *
 * @property created 新建的服务器数量。
 * @property updated 按名称更新的服务器数量。
 * @property skipped 因传输方式或字段无效而跳过的条目数量。
 * @property affectedServerIds 成功新增或更新的服务器稳定 ID。
 * @property error 顶层输入无法处理时的错误；部分条目无效时使用 [skipped] 表达。
 */
data class McpImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val affectedServerIds: Set<String> = emptySet(),
    val error: String? = null,
) {
    val imported: Int
        get() = created + updated
}

/**
 * Agent 导入 MCP 配置并尝试绑定当前会话后的结果。
 *
 * @property importResult 全局 MCP 配置的导入结果。
 * @property conversationActivated 受影响服务器是否已加入当前会话工具选择。
 */
data class McpConversationImportResult(
    val importResult: McpImportResult,
    val conversationActivated: Boolean,
)
