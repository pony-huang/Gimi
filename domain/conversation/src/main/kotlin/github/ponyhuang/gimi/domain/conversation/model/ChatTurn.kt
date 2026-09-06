package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.Serializable

/** 最近一轮发送的持久状态；恢复中的请求不会自动调用模型。 */
@Serializable
enum class ChatTurnStatus { RUNNING, RESTORING, FAILED, INTERRUPTED }

/**
 * 可恢复的最近发送轮次，消息快照包含尚未被上游保存的流式内容。
 *
 * @property id 稳定轮次标识，编辑与重试仍属于同一轮。
 * @property attemptId 本次尝试标识，拒绝旧协程迟到写入。
 * @property userMessage 原始或编辑后的用户请求，附件均已归档。
 * @property messages 当前展示快照，包含之前的历史和本轮输出。
 * @property hasToolCalls 是否可能已经执行工具；重试前需要用户确认。
 */
@Serializable
data class ChatTurn(
    val id: String,
    val attemptId: String,
    val sessionId: String,
    val userMessage: Message,
    val messages: List<Message>,
    val status: ChatTurnStatus = ChatTurnStatus.RUNNING,
    val hasToolCalls: Boolean = false,
) {
    val canRetry: Boolean get() = status == ChatTurnStatus.FAILED || status == ChatTurnStatus.INTERRUPTED
    val history: List<Message> get() = messages.takeWhile { it.id != userMessage.id }
}
