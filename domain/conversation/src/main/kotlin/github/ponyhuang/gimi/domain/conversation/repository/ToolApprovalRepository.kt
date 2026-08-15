package github.ponyhuang.gimi.domain.conversation.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 工具确认策略的全局偏好：哪些工具已被用户「总是允许」，以及是否开启 Full access
 * （所有需要确认的工具调用自动放行）。
 *
 * 与 `domain.toolauthorization` 的启用/禁用语义不同：这里不改变工具是否声明给模型，
 * 只影响 ADK 确认协议层的自动响应。
 */
interface ToolApprovalRepository {
    /** 全局「总是允许」的工具名白名单（跨会话持久化）。 */
    val alwaysAllowedToolNames: StateFlow<Set<String>>

    /** Full access：开启后所有工具确认自动放行。 */
    val fullAccess: StateFlow<Boolean>

    fun setAlwaysAllowed(toolName: String)

    fun removeAlwaysAllowed(toolName: String)

    fun setFullAccess(enabled: Boolean)

    /** fullAccess 开启，或 toolName 在白名单中。 */
    fun isAutoApproved(toolName: String): Boolean
}
