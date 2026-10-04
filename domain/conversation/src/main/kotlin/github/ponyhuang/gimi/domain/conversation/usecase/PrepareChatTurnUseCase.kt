package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import java.util.UUID
import javax.inject.Inject

/** 准备新消息或原执行的重试，不改写会话历史。 */
class PrepareChatTurnUseCase @Inject constructor(
    private val attachments: ChatAttachmentRepository,
) {
    /** 重试保留原消息与部分输出；是否恢复执行由 ADK 决定。 */
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        drafts: List<DraftAttachment>,
        history: List<Message>,
        retry: ChatTurn? = null,
    ): ChatTurn {
        if (retry != null) return retry.copy(status = ChatTurnStatus.RUNNING)
        val userMessage = Messages.fromUser(text, attachments.read(sessionId, drafts))
        return ChatTurn(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            userMessage = userMessage,
            messages = history + userMessage,
        )
    }
}
