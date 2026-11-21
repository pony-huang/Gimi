package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.runtime.ChatbotConversationPartition
import github.ponyhuang.gimi.domain.conversation.chatbot.ChatbotConversationResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ChatbotConversationResolver] 的默认实现：在 Chatbot 分区上解析「当前 Chatbot 会话」。
 *
 * 与助手的 [DefaultConversationSessionResolver] 不同，这里不做模型适配——Chatbot 会话的
 * 模型由 Chatbot 规则在文本首条提交时锁定（语音先建的会话不预填模型），也不解析工具配置
 * （Chatbot 语音无工具，文本侧选项由 ChatbotViewModel 自管）。
 */
@Singleton
class DefaultChatbotConversationResolver @Inject constructor(
    @ChatbotConversationPartition private val conversations: ConversationRepository,
) : ChatbotConversationResolver {
    private val mutex = Mutex()

    override suspend fun resolveCurrentOrCreate(): String = mutex.withLock {
        val remembered = conversations.lastConversationId()
        if (!remembered.isNullOrBlank()) {
            if (conversations.loadMessages(remembered) != null) {
                return@withLock activateLocked(remembered)
            }
            // 记录的会话已不存在（删除/清理），先丢弃悬挂元数据再回退列表。
            conversations.discardConversationMetadata(remembered)
        }
        conversations.listConversations().firstOrNull()?.id?.let { id ->
            if (conversations.loadMessages(id) != null) {
                return@withLock activateLocked(id)
            }
        }
        createLocked()
    }

    override suspend fun activate(sessionId: String): String? = mutex.withLock {
        if (sessionId.isBlank()) return@withLock null
        if (conversations.loadMessages(sessionId) == null) return@withLock null
        activateLocked(sessionId)
    }

    /** 校验已确认存在后激活；激活失败仍返回 sessionId，不阻塞通话。 */
    private suspend fun activateLocked(sessionId: String): String {
        conversations.activateConversation(sessionId, defaultModel = "")
        return sessionId
    }

    private suspend fun createLocked(): String {
        val sessionId = conversations.createConversation(
            initialModel = "",
            activate = true,
            initialToolConfiguration = null,
        )
        check(sessionId.isNotBlank()) { "Unable to create the current Chatbot conversation." }
        return sessionId
    }
}
