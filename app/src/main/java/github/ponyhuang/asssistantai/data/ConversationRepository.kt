package github.ponyhuang.asssistantai.data

import android.util.Log
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.asssistantai.model.Conversation
import github.ponyhuang.asssistantai.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ADK [Session] ↔ UI [Conversation] / [Message] 的映射层。
 *
 * 作为 UI 层与持久化层之间的唯一入口：
 * - `listConversations()` / `refresh()` 维护一个 `StateFlow<List<Conversation>>`，UI 通过 `MainScreen` 订阅。
 * - `loadMessages(sessionId)` 把 Room 里的 events 还原为 `List<Message>`，供 `ChatViewModel.switchSession` 灌回消息列表。
 * - `createConversation()` / `deleteConversation(sessionId)` 是新建 / 删除的写操作。
 *
 * 所有方法 MUST 包 try/catch 捕获 Room 异常并 `Log.w` 告警 — 不允许向上抛到 UI / Activity，避免 SQLite 故障导致 app crash。
 */
class ConversationRepository(
    private val appName: String,
    private val userId: String,
    private val sessionService: SessionService,
    private val metadataDao: ConversationMetadataDao,
) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /**
     * 拉取最新会话列表并 emit 到 [conversations]。
     *
     * 排序：按 `session.lastUpdateTime` 降序（最近活跃的在前）。失败的调用 MUST 保留旧列表（不 emit 空列表覆盖），仅 `Log.w`。
     */
    suspend fun refresh() {
        try {
            val list = listConversationsInternal()
            _conversations.value = list
        } catch (t: Throwable) {
            Log.w(TAG, "refresh() failed: ${t::class.simpleName}: ${t.message}")
        }
    }

    /** Reloads and replaces one changed session without rebuilding the entire drawer list. */
    suspend fun refreshConversation(sessionId: String) {
        if (sessionId.isBlank()) return
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        try {
            val session = sessionService.getSession(key) ?: return
            val updated = session.toConversation(metadataFor(sessionId))
            _conversations.value = _conversations.value
                .filterNot { it.id == sessionId }
                .plus(updated)
                .sortedByDescending { it.timestamp }
        } catch (t: Throwable) {
            Log.w(TAG, "refreshConversation($sessionId) failed: ${t::class.simpleName}: ${t.message}")
        }
    }

    /**
     * 同步拉取一次（不走 StateFlow）。供 `ChatViewModel.switchSession` 等需要在 launch 中获取列表的场景。
     */
    suspend fun listConversations(): List<Conversation> =
        try {
            listConversationsInternal()
        } catch (t: Throwable) {
            Log.w(TAG, "listConversations() failed: ${t::class.simpleName}: ${t.message}")
            emptyList()
        }

    /**
     * 加载某个 session 的历史 messages（按 timestamp 升序）。
     *
     * 返回值语义：
     * - `null`：session 不存在（`sessionService.getSession` 返回 null），调用方负责 fallback 到 `createConversation`。
     * - 空列表：session 存在但没有任何 event（刚创建的空会话），调用方不应再创建。
     * - 非空列表：历史 messages。
     */
    suspend fun loadMessages(sessionId: String): List<Message>? {
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        val session: Session = try {
            sessionService.getSession(key) ?: return null
        } catch (t: Throwable) {
            Log.w(TAG, "loadMessages($sessionId) failed: ${t::class.simpleName}: ${t.message}")
            return null
        }
        return try {
            EventMapper.fromSession(session)
        } catch (t: Throwable) {
            Log.w(TAG, "EventMapper.fromSession($sessionId) failed: ${t::class.simpleName}: ${t.message}")
            emptyList()
        }
    }

    /** Returns the session selected at the previous app shutdown, if one was recorded. */
    suspend fun lastConversationId(): String? =
        try {
            metadataDao.getLast()?.sessionId
        } catch (t: Throwable) {
            Log.w(TAG, "lastConversationId() failed: ${t::class.simpleName}: ${t.message}")
            null
        }

    /** Marks an existing session as current and returns its stored model selection payload. */
    suspend fun activateConversation(sessionId: String, defaultModel: String): String =
        try {
            val metadata = metadataDao.activate(sessionId, defaultModel)
            refreshConversation(sessionId)
            metadata.model
        } catch (t: Throwable) {
            Log.w(TAG, "activateConversation($sessionId) failed: ${t::class.simpleName}: ${t.message}")
            defaultModel
        }

    /** Persists the selected model for a conversation and refreshes its drawer row. */
    suspend fun setConversationModel(sessionId: String, model: String) {
        if (sessionId.isBlank()) return
        try {
            metadataDao.setModel(sessionId, model)
            refreshConversation(sessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "setConversationModel($sessionId) failed: ${t::class.simpleName}: ${t.message}")
        }
    }

    /** Removes stale metadata after an ADK session can no longer be found. */
    suspend fun discardConversationMetadata(sessionId: String) {
        if (sessionId.isBlank()) return
        try {
            metadataDao.delete(sessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "discardConversationMetadata($sessionId) failed: ${t::class.simpleName}: ${t.message}")
        }
    }

    /**
     * 创建一个新会话，返回生成的 sessionId。
     */
    suspend fun createConversation(initialModel: String = ""): String {
        val key = SessionKey(appName = appName, userId = userId, id = null)
        return try {
            val created = sessionService.createSession(key)
            val sessionId = created.key.id.orEmpty()
            if (sessionId.isNotBlank()) {
                try {
                    metadataDao.activate(sessionId, initialModel)
                } catch (t: Throwable) {
                    Log.w(TAG, "createConversation($sessionId) metadata write failed: ${t::class.simpleName}: ${t.message}")
                }
            }
            refresh()
            sessionId
        } catch (t: Throwable) {
            Log.w(TAG, "createConversation() failed: ${t::class.simpleName}: ${t.message}")
            ""
        }
    }

    /**
     * 删除一个会话，触发 [refresh]。
     */
    suspend fun deleteConversation(sessionId: String) {
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        try {
            sessionService.deleteSession(key)
            metadataDao.delete(sessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "deleteConversation($sessionId) failed: ${t::class.simpleName}: ${t.message}")
        }
        refresh()
    }

    private suspend fun listConversationsInternal(): List<Conversation> {
        val response = sessionService.listSessions(appName = appName, userId = userId)
        val metadataBySessionId = metadataBySessionId()
        val sessionIds = response.sessions.mapTo(mutableSetOf()) { it.key.id.orEmpty() }
        for (metadataSessionId in metadataBySessionId.keys) {
            if (metadataSessionId !in sessionIds) {
                discardConversationMetadata(metadataSessionId)
            }
        }
        return response.sessions
            .sortedByDescending { it.lastUpdateTime.toEpochMilliseconds() }
            .map { session -> session.toConversation(metadataBySessionId[session.key.id.orEmpty()]) }
    }

    private suspend fun metadataBySessionId(): Map<String, ConversationMetadataEntity> =
        try {
            metadataDao.getAll().associateBy { it.sessionId }
        } catch (t: Throwable) {
            Log.w(TAG, "metadataBySessionId() failed: ${t::class.simpleName}: ${t.message}")
            emptyMap()
        }

    private suspend fun metadataFor(sessionId: String): ConversationMetadataEntity? =
        try {
            metadataDao.get(sessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "metadataFor($sessionId) failed: ${t::class.simpleName}: ${t.message}")
            null
        }

    /**
     * ADK [Session] → UI [Conversation]：
     * - title：优先使用 ADK callback 写入 session state 的自动标题；历史会话回退到首条用户消息。
     * - lastMessage：最后一条非空 text（按事件遍历顺序，截断 64 字符）。
     * - timestamp：`session.lastUpdateTime.toEpochMilliseconds()`。
     */
    private fun Session.toConversation(metadata: ConversationMetadataEntity?): Conversation {
        val sessionId = key.id.orEmpty()
        val persistedTitle = state[CONVERSATION_TITLE_STATE_KEY] as? String
        val titleSource: String? = events.firstOrNull { it.author == "user" }
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }
        val fallbackTitle = titleSource?.take(TITLE_MAX_LENGTH)?.let {
            if (titleSource.length > TITLE_MAX_LENGTH) "$it…" else it
        } ?: events.firstOrNull { event ->
            event.author == "user" && event.content?.parts.orEmpty().any { it.inlineData?.mimeType?.startsWith("image/") == true }
        }?.let { "图片消息" } ?: "新对话"
        val title = persistedTitle?.takeIf(String::isNotBlank) ?: fallbackTitle

        val lastMessage: String = events.lastOrNull { e ->
            val parts = e.content?.parts.orEmpty()
            parts.any { !it.text.isNullOrEmpty() }
        }?.content?.parts?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }
            ?.let {
                if (it.length > LAST_MESSAGE_MAX_LENGTH) it.take(LAST_MESSAGE_MAX_LENGTH) + "…" else it
            }
            ?: ""

        return Conversation(
            id = sessionId,
            title = title,
            lastMessage = lastMessage,
            model = metadata?.model.orEmpty(),
            isLast = metadata?.isLast ?: false,
            timestamp = lastUpdateTime.toEpochMilliseconds(),
        )
    }

    companion object {
        private const val TAG: String = "ConversationRepository"
        private const val TITLE_MAX_LENGTH: Int = 32
        private const val LAST_MESSAGE_MAX_LENGTH: Int = 64
        private const val CONVERSATION_TITLE_STATE_KEY: String = "conversation.title"
    }
}
