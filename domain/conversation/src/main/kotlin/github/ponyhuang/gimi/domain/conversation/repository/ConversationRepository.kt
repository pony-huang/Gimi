package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain boundary for conversation history and app-owned conversation metadata.
 *
 * Implementations may use ADK, Room, or another store, but those types must never cross this
 * interface. A null message history means the conversation no longer exists; an empty history
 * means it exists and has no persisted events.
 */
interface ConversationRepository {
    val conversations: StateFlow<List<Conversation>>
    /** 进程内各会话的内容版本；慢订阅者或暂未展示的会话也不会丢失失效信息。 */
    val conversationContentRevisions: StateFlow<Map<String, Long>>

    suspend fun refresh()
    suspend fun refreshConversation(sessionId: String)
    suspend fun listConversations(): List<Conversation>
    suspend fun loadMessages(sessionId: String): List<Message>?
    suspend fun lastConversationId(): String?
    suspend fun activateConversation(sessionId: String, defaultModel: String): String
    suspend fun setConversationModel(sessionId: String, model: String)
    suspend fun conversationToolConfiguration(sessionId: String): ConversationToolConfiguration?
    suspend fun setConversationToolConfiguration(
        sessionId: String,
        configuration: ConversationToolConfiguration,
    ): Boolean
    suspend fun discardConversationMetadata(sessionId: String)
    suspend fun createConversation(
        initialModel: String = "",
        activate: Boolean = true,
        initialToolConfiguration: ConversationToolConfiguration? = null,
    ): String
    suspend fun deleteConversation(sessionId: String)

    /** 外部执行入口退出时使历史缓存失效，包含失败或取消前已经落盘的部分结果。 */
    fun notifyConversationContentChanged(sessionId: String)
}

interface ChatDisplayRepository {
    val showToolActivity: StateFlow<Boolean>

    fun setShowToolActivity(show: Boolean)
}

/**
 * Global tool loading preference applied to every conversation.
 *
 * This replaced the former per-conversation [ConversationToolConfiguration.toolAccessMode]:
 * tool loading is now a settings-level switch (on = load all enabled tools each turn, off =
 * search and load tools on demand).
 */
interface ToolAccessRepository {
    /** The currently selected global tool loading mode. */
    val defaultToolAccessMode: StateFlow<ToolAccessMode>

    /** Persist and publish the global tool loading mode. */
    fun setDefaultToolAccessMode(mode: ToolAccessMode)
}
