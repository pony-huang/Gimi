package github.ponyhuang.gimi.data.conversation.repository

import android.content.Context
import android.util.Log
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.GetSessionConfig
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.gimi.data.conversation.R
import github.ponyhuang.gimi.data.conversation.local.ConversationMetadataDao
import github.ponyhuang.gimi.data.conversation.local.ConversationMetadataEntity
import github.ponyhuang.gimi.data.conversation.local.ConversationToolConfigurationCodec
import github.ponyhuang.gimi.data.conversation.mapper.EventMapper
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
class AdkConversationRepository(
    private val appName: String,
    private val userId: String,
    private val sessionService: SessionService,
    private val metadataDao: ConversationMetadataDao,
    private val context: Context,
) : ConversationRepository {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _conversationContentRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val conversationContentRevisions: StateFlow<Map<String, Long>> =
        _conversationContentRevisions.asStateFlow()

    override fun notifyConversationContentChanged(sessionId: String) {
        if (sessionId.isBlank()) return
        _conversationContentRevisions.update { revisions ->
            revisions + (sessionId to ((revisions[sessionId] ?: 0L) + 1L))
        }
    }

    /**
     * 拉取最新会话列表并 emit 到 [conversations]。
     *
     * 排序：按 `session.lastUpdateTime` 降序（最近活跃的在前）。失败的调用 MUST 保留旧列表（不 emit 空列表覆盖），仅 `Log.w`。
     */
    override suspend fun refresh() {
        try {
            val list = listConversationsInternal()
            _conversations.value = list
        } catch (t: Throwable) {
            recover(t, "refresh()", Unit)
        }
    }

    /**
     * Reloads and replaces one changed session without rebuilding the entire drawer list.
     *
     * 只取最近 [PREVIEW_EVENT_WINDOW] 条事件：抽屉行需要的预览与标题回退都在末尾事件里，
     * 读全量历史会让每轮成本随会话长度增长。预览顺手写回元数据，使列表刷新无需再读事件。
     */
    override suspend fun refreshConversation(sessionId: String) {
        if (sessionId.isBlank()) return
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        val session = try {
            readForDrawerRow(key)
        } catch (t: Throwable) {
            recover(t, "refreshConversation($sessionId) read", Unit)
            return
        } ?: return
        val metadata = metadataFor(sessionId)
        val preview = session.previewText()
        // 先发布抽屉行，再做附加的元数据写入：预览落库失败不得让标题/预览停在旧值，
        // 否则用户只能等下一次整表刷新才看到变化。
        _conversations.update { conversations ->
            conversations
                .filterNot { it.id == sessionId }
                .plus(
                    session.toConversation(
                        metadata = if (preview.isEmpty()) metadata else metadata?.copy(lastMessage = preview),
                        previousTitle = conversations.firstOrNull { it.id == sessionId }?.title,
                    ),
                )
                .sortedByDescending { it.timestamp }
        }
        if (preview.isNotEmpty() && preview != metadata?.lastMessage) {
            try {
                metadataDao.setLastMessage(sessionId, preview)
            } catch (t: Throwable) {
                recover(t, "setLastMessage($sessionId)", Unit)
            }
        }
    }

    /**
     * 读取抽屉行所需的会话快照。
     *
     * 常规路径只取最近 [PREVIEW_EVENT_WINDOW] 条事件，代价与历史长度无关；只有既没有状态
     * 标题、窗口里也找不到首条用户消息的会话（标题只能靠首条消息回退）才退回整段读取，
     * 保持旧行为而不会让这类会话的标题退化成默认名。
     */
    private suspend fun readForDrawerRow(key: SessionKey): Session? {
        val windowed = sessionService.getSession(key, GetSessionConfig(numRecentEvents = PREVIEW_EVENT_WINDOW))
            ?: return null
        val titleInState = !(windowed.state[CONVERSATION_TITLE_STATE_KEY] as? String).isNullOrBlank()
        val firstUserInWindow = windowed.events.any { event -> event.author == "user" }
        return if (titleInState || firstUserInWindow) windowed else sessionService.getSession(key)
    }

    /**
     * 同步拉取一次（不走 StateFlow）。供 `ChatViewModel.switchSession` 等需要在 launch 中获取列表的场景。
     */
    override suspend fun listConversations(): List<Conversation> =
        try {
            listConversationsInternal()
        } catch (t: Throwable) {
            recover(t, "listConversations()", emptyList())
        }

    /**
     * 加载某个 session 的历史 messages（按 timestamp 升序）。
     *
     * 返回值语义：
     * - `null`：session 不存在（`sessionService.getSession` 返回 null），调用方负责 fallback 到 `createConversation`。
     * - 空列表：session 存在但没有任何 event（刚创建的空会话），调用方不应再创建。
     * - 非空列表：历史 messages。
     */
    override suspend fun loadMessages(sessionId: String): List<Message>? {
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        val session: Session = try {
            sessionService.getSession(key) ?: return null
        } catch (t: Throwable) {
            return recover(t, "loadMessages($sessionId)", null)
        }
        return try {
            EventMapper.fromSession(session)
        } catch (t: Throwable) {
            recover(t, "EventMapper.fromSession($sessionId)", emptyList())
        }
    }

    /** Returns the session selected at the previous app shutdown, if one was recorded. */
    override suspend fun lastConversationId(): String? =
        try {
            metadataDao.getLast()?.sessionId
        } catch (t: Throwable) {
            recover(t, "lastConversationId()", null)
        }

    /** Marks an existing session as current and returns its stored model selection payload. */
    override suspend fun activateConversation(sessionId: String, defaultModel: String): String =
        try {
            val metadata = metadataDao.activate(sessionId, defaultModel)
            refreshConversation(sessionId)
            metadata.model
        } catch (t: Throwable) {
            recover(t, "activateConversation($sessionId)", defaultModel)
        }

    /** Persists the selected model for a conversation and refreshes its drawer row. */
    override suspend fun setConversationModel(sessionId: String, model: String) {
        if (sessionId.isBlank()) return
        try {
            metadataDao.setModel(sessionId, model)
            refreshConversation(sessionId)
        } catch (t: Throwable) {
            recover(t, "setConversationModel($sessionId)", Unit)
        }
    }

    override suspend fun conversationToolConfiguration(
        sessionId: String,
    ): ConversationToolConfiguration? {
        if (sessionId.isBlank()) return null
        return try {
            ConversationToolConfigurationCodec.decode(metadataDao.get(sessionId)?.toolConfigurationJson)
        } catch (t: Throwable) {
            recover(t, "conversationToolConfiguration($sessionId)", null)
        }
    }

    override suspend fun setConversationToolConfiguration(
        sessionId: String,
        configuration: ConversationToolConfiguration,
    ): Boolean {
        if (sessionId.isBlank()) return false
        return try {
            metadataDao.setToolConfiguration(
                sessionId,
                ConversationToolConfigurationCodec.encode(configuration),
            )
            true
        } catch (t: Throwable) {
            recover(t, "setConversationToolConfiguration($sessionId)", false)
        }
    }

    /** Removes stale metadata after an ADK session can no longer be found. */
    override suspend fun discardConversationMetadata(sessionId: String) {
        if (sessionId.isBlank()) return
        try {
            metadataDao.delete(sessionId)
            _conversationContentRevisions.update { it - sessionId }
        } catch (t: Throwable) {
            recover(t, "discardConversationMetadata($sessionId)", Unit)
        }
    }

    /**
     * 创建一个新会话，返回生成的 sessionId。
     */
    override suspend fun createConversation(
        initialModel: String,
        activate: Boolean,
        initialToolConfiguration: ConversationToolConfiguration?,
    ): String {
        val key = SessionKey(appName = appName, userId = userId, id = null)
        return try {
            val created = sessionService.createSession(key)
            val sessionId = created.key.id.orEmpty()
            if (sessionId.isNotBlank()) {
                try {
                    if (activate) {
                        val metadata = metadataDao.activate(sessionId, initialModel)
                        if (initialToolConfiguration != null) {
                            metadataDao.upsert(
                                metadata.copy(
                                    toolConfigurationJson =
                                        ConversationToolConfigurationCodec.encode(
                                            initialToolConfiguration,
                                        ),
                                ),
                            )
                        }
                    } else {
                        metadataDao.upsert(
                            ConversationMetadataEntity(
                                sessionId = sessionId,
                                model = initialModel,
                                isLast = false,
                    toolConfigurationJson = initialToolConfiguration
                        ?.let(ConversationToolConfigurationCodec::encode)
                        .orEmpty(),
                            ),
                        )
                    }
                } catch (t: Throwable) {
                    recover(t, "createConversation($sessionId) metadata write", Unit)
                }
            }
            refresh()
            sessionId
        } catch (t: Throwable) {
            recover(t, "createConversation()", "")
        }
    }

    /**
     * 删除一个会话，触发 [refresh]。
     */
    override suspend fun deleteConversation(sessionId: String) {
        val key = SessionKey(appName = appName, userId = userId, id = sessionId)
        // 删除失败必须交给调用方提示和重试，不能把未删除的会话当作成功清理。
        sessionService.deleteSession(key)
        metadataDao.delete(sessionId)
        _conversationContentRevisions.update { it - sessionId }
        _conversations.update { list -> list.filterNot { it.id == sessionId } }
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
            recover(t, "metadataBySessionId()", emptyMap())
        }

    private suspend fun metadataFor(sessionId: String): ConversationMetadataEntity? =
        try {
            metadataDao.get(sessionId)
        } catch (t: Throwable) {
            recover(t, "metadataFor($sessionId)", null)
        }

    /**
     * ADK [Session] → UI [Conversation]：
     * - title：优先使用 ADK callback 写入 session state 的自动标题；历史会话回退到首条用户消息。
     * - lastMessage：最后一条非空 text（按事件遍历顺序，截断 64 字符）。
     * - timestamp：`session.lastUpdateTime.toEpochMilliseconds()`。
     */
    private fun Session.toConversation(
        metadata: ConversationMetadataEntity?,
        previousTitle: String? = null,
    ): Conversation {
        val sessionId = key.id.orEmpty()
        val persistedTitle = state[CONVERSATION_TITLE_STATE_KEY] as? String
        val titleSource: String? = events.firstOrNull { it.author == "user" }
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }
        val fallbackTitle = titleSource?.take(TITLE_MAX_LENGTH)?.let {
            if (titleSource.length > TITLE_MAX_LENGTH) "$it…" else it
        } ?: events.firstOrNull { event ->
            event.author == "user" && event.content?.parts.orEmpty().any {
                it.inlineData != null || it.fileData != null
            }
        }?.let { context.getString(R.string.conversation_attachment_message_title) }
            ?: context.getString(R.string.conversation_default_title)
        // 窗口读不到首条用户消息时保留既有标题，避免把已有标题降级成默认名。
        val defaultTitle = context.getString(R.string.conversation_default_title)
        val title = persistedTitle?.takeIf(String::isNotBlank)
            ?: fallbackTitle.takeUnless { it == defaultTitle }
            ?: previousTitle?.takeIf(String::isNotBlank)
            ?: fallbackTitle

        val lastMessage = previewTextFromEvents().ifEmpty { metadata?.lastMessage.orEmpty() }

        return Conversation(
            id = sessionId,
            title = title,
            lastMessage = lastMessage,
            model = metadata?.model.orEmpty(),
            isLast = metadata?.isLast ?: false,
            timestamp = lastUpdateTime.toEpochMilliseconds(),
        )
    }

    /**
     * 会话最后一条非空文本，作为抽屉行预览；超出上限按行宽截断。
     *
     * 只读传入会话携带的事件，因此调用方用 [GetSessionConfig] 限定窗口后，代价与窗口大小
     * 而不是历史长度相关。
     */
    private fun Session.previewText(): String = previewTextFromEvents()

    private fun Session.previewTextFromEvents(): String = events.lastOrNull { event ->
        val parts = event.content?.parts.orEmpty()
        parts.any { !it.text.isNullOrEmpty() }
    }?.content?.parts?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }
        ?.let {
            if (it.length > LAST_MESSAGE_MAX_LENGTH) it.take(LAST_MESSAGE_MAX_LENGTH) + "…" else it
        }
        ?: ""

    companion object {
        private const val TAG: String = "ConversationRepository"
        private const val TITLE_MAX_LENGTH: Int = 32
        private const val LAST_MESSAGE_MAX_LENGTH: Int = 64
        private const val CONVERSATION_TITLE_STATE_KEY: String = "conversation.title"

        /** 刷新单行会话时读取的最近事件条数；预览与标题回退都在这个窗口内。 */
        private const val PREVIEW_EVENT_WINDOW: Int = 20
    }
}

private fun <T> recover(failure: Throwable, operation: String, fallback: T): T {
    if (failure is CancellationException) throw failure
    Log.w(
        "ConversationRepository",
        "$operation failed: ${failure::class.simpleName}: ${failure.message}",
    )
    return fallback
}
