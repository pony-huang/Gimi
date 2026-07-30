package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import kotlinx.coroutines.flow.SharedFlow
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
    val conversationContentUpdates: SharedFlow<String>

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

    /** Publish only after an out-of-process-style writer has persisted a complete turn. */
    fun notifyConversationContentChanged(sessionId: String)
}

interface ChatDisplayRepository {
    val showToolActivity: StateFlow<Boolean>

    /**
     * 夜间模式覆盖值；`null` 表示跟随系统（官方文档推荐的默认项）。
     * 用户一旦拨动开关就写入明确的浅/深偏好，之后不再随系统变化。
     */
    val darkThemeOverride: StateFlow<Boolean?>

    fun setShowToolActivity(show: Boolean)

    fun setDarkThemeOverride(enabled: Boolean)
}
