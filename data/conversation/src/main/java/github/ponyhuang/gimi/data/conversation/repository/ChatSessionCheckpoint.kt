package github.ponyhuang.gimi.data.conversation.repository

import kotlinx.serialization.Serializable

/**
 * 发送前对会话做的可回退检查点。
 *
 * 仅捕获会话级状态与会话事件；不含跨会话共享的 app/user 级状态，恢复时也只回退到会话级
 * 资源，符合 ADK 的“rewind 只影响 session 级、不触碰全局资源”语义。事件以原始 JSON 保存，
 * 恢复时不再重新执行工具事件的副作用。
 *
 * @property sessionId 会话 ID。
 * @property stateJson 会话状态（`session.state`）的 JSON。
 * @property createTime 会话创建时间（毫秒）。
 * @property updateTime 检查点时刻的会话更新时间（毫秒）。
 * @property events 检查点时刻的会话事件。
 */
@Serializable
data class ChatSessionCheckpoint(
    val sessionId: String,
    val stateJson: String,
    val createTime: Long,
    val updateTime: Long,
    val events: List<CheckpointEvent>,
)

/** 会话事件在检查点时刻的原样快照。 */
@Serializable
data class CheckpointEvent(
    val id: String,
    val invocationId: String?,
    val timestamp: Long,
    val eventData: String,
)

/** SDK 存储边界：检查点的抓取与恢复必须是完整事务，不能暴露“会话暂时不存在”的中间态。 */
interface ChatSessionCheckpointStore {
    suspend fun capture(sessionId: String): ChatSessionCheckpoint

    suspend fun restore(checkpoint: ChatSessionCheckpoint)
}
