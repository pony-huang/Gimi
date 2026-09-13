package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import java.util.UUID
import javax.inject.Inject

/** 在持有会话运行锁时准备请求，验证附件并构造当前进程内的 ADK 回滚边界。 */
class PrepareChatTurnUseCase @Inject constructor(
    private val attachments: ChatAttachmentRepository,
) {
    /**
     * 构造一次可运行的发送尝试，不做任何网络/模型调用。
     *
     * - 纯重试（[reuseOriginal]）：剔除历史还原时标记缺失的附件，校验其余附件仍可读后
     *   原样复用 [retry.userMessage]，并把历史回退到该轮之前的消息，避免再次发送时在
     *   模型上下文里重复用户消息。缺失附件直接离开重发内容，而不是让校验失败阻断重试。
     * - 编辑（[retry] 非空、[reuseOriginal] false）：重新读取草稿附件，保留原消息 id 但替换
     *   文本与附件，历史同样回退到该轮之前。
     * - 首次发送（[retry] 为空）：按草稿读取附件并新建用户消息。
     *
     * 附件读取/校验失败或取消直接抛给调用方；本用例只准备输入，不管理 Agent 生命周期。
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
            // 缺失附件的载荷文件已不可用（历史还原时标记），直接从重发内容中剔除并只校验
            // 其余附件；失效路径不进入模型请求。
            val resendable = retry.userMessage.fileAttachments.filterNot { it.isMissing }
            attachments.validateSaved(resendable)
            userMessage = retry.userMessage.copy(fileAttachments = resendable)
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
        return ChatTurn(
            id = retry?.id ?: UUID.randomUUID().toString(),
            sessionId = sessionId,
            userMessage = userMessage,
            messages = effectiveHistory + userMessage,
            rewindBeforeInvocationId = retry?.rewindBeforeInvocationId,
        )
    }
}
