package github.ponyhuang.gimi.domain.mcp.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class McpTransport {
    SSE,
    STREAMABLE_HTTP,
}

@Serializable
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
 * @property credentialRequiredServerIds 已导入但仍需用户补充认证凭据的服务器 ID。
 * @property error 顶层输入无法处理时的错误；部分条目无效时使用 [skipped] 表达。
 */
data class McpImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val affectedServerIds: Set<String> = emptySet(),
    val credentialRequiredServerIds: Set<String> = emptySet(),
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

/**
 * 当前会话待补 MCP 认证信息的更新结果。
 *
 * @property updated 认证信息是否已经写入 MCP server 配置。
 * @property serverName 被更新的服务器名称；无法定位服务器时为空。
 * @property error 无法更新或无法清理会话待补标记时的安全错误信息。
 */
data class McpCredentialUpdateResult(
    val updated: Boolean,
    val serverName: String? = null,
    val error: String? = null,
)

/**
 * Agent 手动配置单个 MCP server 并尝试同步当前会话选择的结果。
 *
 * 与批量导入一样按名称定位：同名已存在则更新，否则新建。会话选择是否写入取决于
 * [enabled]：启用时把 server 加入当前会话工具选择，停用时从选择中移除。
 *
 * @property serverId 被创建或更新的服务器稳定 ID。
 * @property serverName 安全展示给模型的服务器名称（不包含端点或凭据）。
 * @property created 是否新建了服务器。
 * @property updated 是否更新了已有服务器。
 * @property conversationActivated 会话选择是否已反映本次请求（启用为加入、停用为移除）。
 * @property error 字段无效或持久化失败时的安全错误信息。
 */
data class McpManualConfigurationResult(
    val serverId: String,
    val serverName: String,
    val created: Boolean,
    val updated: Boolean,
    val conversationActivated: Boolean,
    val error: String? = null,
) {
    /** 配置本身是否成功落盘；与会话激活状态无关，避免停用场景误报失败。 */
    val configured: Boolean
        get() = error == null && (created || updated)
}
