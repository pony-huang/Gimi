package github.ponyhuang.gimi.domain.conversation.model

/** 当前进程内最近一轮发送的状态。 */
enum class ChatTurnStatus { RUNNING, FAILED }

/**
 * 当前进程内可恢复的最近发送轮次，消息快照包含尚未被上游保存的流式内容。
 *
 * @property id 稳定轮次标识，重试仍属于同一轮。
 * @property userMessage 原始用户请求，附件均已归档。
 * @property messages 当前展示快照，包含之前的历史和本轮输出。
 */
data class ChatTurn(
    val id: String,
    val sessionId: String,
    val userMessage: Message,
    val messages: List<Message>,
    val status: ChatTurnStatus = ChatTurnStatus.RUNNING,
) {
    val canRetry: Boolean get() = status == ChatTurnStatus.FAILED
}
