package github.ponyhuang.asssistantai.domain.conversation.repository

import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.model.DraftAttachment
import github.ponyhuang.asssistantai.domain.conversation.model.FileAttachment
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.Flow

interface ChatAgentRepository {
    suspend fun send(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        fileAttachments: List<FileAttachment>,
        toolConfiguration: ConversationToolConfiguration? = null,
    ): Flow<ChatRunEvent>

    suspend fun respondToToolConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<ChatRunEvent>

    suspend fun releaseSession(sessionId: String)
}

/**
 * 管理聊天附件从“输入栏草稿”到“可发送消息附件”的生命周期。
 *
 * 附件选择完成后，输入栏只持有轻量的 [DraftAttachment]。它记录应用私有草稿文件的
 * 引用、文件名、MIME 和大小，不把完整文件字节放进 Compose 状态。
 *
 * 发送时调用 [read]，将草稿文件转换为包含实际内容的 [FileAttachment]，供聊天协议
 * 内联发送；同时为已发送消息建立按会话保存的载荷引用。发送成功后调用 [deleteDrafts]
 * 清理临时草稿。删除整个会话时调用 [deleteSession] 清理该会话持久化的附件。
 *
 * 大致流程：
 * ```
 * 系统文件选择器
 *   -> DraftAttachment（输入栏草稿引用）
 *   -> read()
 *   -> FileAttachment（可发送的完整附件）
 *   -> deleteDrafts()（发送成功后）
 *
 * 删除会话
 *   -> deleteSession()
 * ```
 *
 * 该接口只定义附件生命周期规则；文件系统、图片压缩和线程切换等实现细节属于
 * data 层。
 */
interface ChatAttachmentRepository {
    /**
     * 读取并准备本轮要发送的附件。
     *
     * 实现需要解析 [attachments] 指向的应用私有草稿文件，生成包含实际字节的
     * [FileAttachment]。图片可以在此阶段执行发送前压缩；音频和文档通常保留原始内容。
     * 返回结果的顺序必须与传入草稿顺序一致。
     *
     * 此方法不会删除传入的草稿。调用方只有在消息发送成功后才能调用 [deleteDrafts]；
     * 如果读取或发送失败，草稿文件仍可用于错误处理或后续清理。
     *
     * @param sessionId 当前聊天会话 ID，用于把已准备的附件归档到对应会话。
     * @param attachments 输入栏当前选择的轻量草稿附件。
     * @return 可交给聊天请求层内联发送的完整附件。
     * @throws Exception 草稿不存在、内容已变化、无法读取或无法转换时抛出。
     */
    suspend fun read(
        sessionId: String,
        attachments: List<DraftAttachment>,
    ): List<FileAttachment>

    /**
     * 删除已经不再需要的输入栏草稿文件。
     *
     * 典型调用时机包括：附件被用户移除、被其他类别附件替换，或消息发送成功。
     * 实现应只删除应用管理的草稿文件；重复调用或文件已经不存在时应安全返回。
     *
     * @param attachments 要清理的草稿附件。
     */
    suspend fun deleteDrafts(attachments: List<DraftAttachment>)

    /**
     * 删除某个会话保存的全部附件载荷。
     *
     * 只负责附件文件，不负责删除会话记录、消息或远端资源。通常由“删除会话”流程调用。
     * 如果会话目录不存在，调用应安全返回。
     *
     * @param sessionId 要清理附件的聊天会话 ID。
     */
    suspend fun deleteSession(sessionId: String)
}
