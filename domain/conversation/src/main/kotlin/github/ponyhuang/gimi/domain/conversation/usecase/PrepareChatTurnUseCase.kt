package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatTurnRepository
import javax.inject.Inject

/** 在持有会话运行锁时准备请求，先验证附件，最后持久化并恢复检查点。 */
class PrepareChatTurnUseCase @Inject constructor(
    private val turns: ChatTurnRepository,
    private val attachments: ChatAttachmentRepository,
    private val runner: ChatAgentRepository,
) {
    /**
     * 构造一次可运行的发送尝试，不做任何网络/模型调用。
     *
     * - 纯重试（[reuseOriginal]）：校验上次保存的附件仍可读，原样复用 [retry.userMessage]，
     *   并把历史回退到该轮之前的消息，避免再次发送时在模型上下文里重复用户消息。
     * - 编辑（[retry] 非空、[reuseOriginal] false）：重新读取草稿附件，保留原消息 id 但替换
     *   文本与附件，历史同样回退到该轮之前。
     * - 首次发送（[retry] 为空）：按草稿读取附件并新建用户消息。
     *
     * 附件读取/校验失败或协程取消会直接向上抛出，不会释放会话绑定或记录新尝试；
     * 只有成功后才调用 [ChatAgentRepository.releaseSession] 丢弃旧绑定的生命周期。
     */
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        drafts: List<DraftAttachment>,
        history: List<Message>,
        retry: ChatTurn? = null,
        reuseOriginal: Boolean = false,
    ): ChatTurn {
        val userMessage: Message
        val effectiveHistory: List<Message>
        if (reuseOriginal && retry != null) {
            attachments.validateSaved(retry.userMessage.fileAttachments)
            userMessage = retry.userMessage
            effectiveHistory = retry.history
        } else {
            val prepared = attachments.read(sessionId, drafts)
            userMessage = retry?.let { previous ->
                previous.userMessage.copy(
                    textParts = if (text.isBlank()) emptyList() else {
                        listOf(TextPart(text = text))
                    },
                    fileAttachments = prepared,
                    timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
            } ?: Messages.fromUser(text = text, fileAttachments = prepared)
            effectiveHistory = retry?.history ?: history
        }
        runner.releaseSession(sessionId)
        return turns.begin(
            sessionId = sessionId,
            userMessage = userMessage,
            history = effectiveHistory,
            retryTurnId = retry?.id,
        )
    }
}
