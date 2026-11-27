package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.chatbot.ChatbotConversationResolver
import github.ponyhuang.gimi.domain.conversation.model.NATIVE_REALTIME_TRANSCRIPT_METADATA_KEY
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.runtime.ChatbotConversationPartition
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Chatbot 三入口共享的会话写入组件：把一轮的用户/模型文字事件写入 Chatbot 分区
 * （[AgentSessionIdentity.CHATBOT_USER_ID]）的「当前 Chatbot 会话」。
 *
 * - 级联与原生实时均经 [acquire][Call.acquire] 恢复最近 64 条历史；原生实时重连时经
 *   [currentHistory][Call.currentHistory] 刷新本次通话新增的轮次。工具调用不写入：Chatbot 上下文只承载对话文字。
 * - 未显式绑定会话时经 [ChatbotConversationResolver] 解析当前 Chatbot 会话，
 *   与文本 chatbot 共享同一段对话。
 */
class ChatbotConversationWriter @Inject constructor(
    private val sessions: SessionService,
    @ChatbotConversationPartition private val conversations: ConversationRepository,
    private val resolver: ChatbotConversationResolver,
    private val gate: AgentRuntimeGate,
) {
    internal fun bind(conversationId: String?): Call = Call(conversationId)

    internal inner class Call(private var conversationId: String?) {
        private var lease: AgentRunLease? = null
        private val pending = ArrayDeque<Event>()

        /**
         * 解析并记住目标会话 ID；[currentHistory] 等不申请租约的入口也必须先解析，
         * 否则后续 [flush] 会在空 ID 上失败并静默丢转写。
         */
        suspend fun resolve(): String {
            if (conversationId.isNullOrBlank()) {
                conversationId = resolver.resolveCurrentOrCreate()
            }
            return requireNotNull(conversationId)
        }

        /** 取当前 Chatbot 会话的写入租约；级联链路用返回的历史作为本轮上下文。 */
        suspend fun acquire(): List<Content> {
            if (lease != null) return emptyList()
            try {
                val id = resolve()
                lease = gate.acquire(AgentTaskSource.REALTIME_VOICE, id, AgentTaskPhase.WAITING_FOR_INPUT)
                val session = requireNotNull(sessions.getSession(key())) { "Conversation no longer exists" }
                return historyFrom(session)
            } catch (failure: Exception) {
                release()
                when (failure) {
                    is CancellationException -> throw failure
                    is AgentSessionBusyException -> throw VoiceAudioBackendException(RealtimeVoiceFailure.ConversationBusy, cause = failure)
                    else -> throw VoiceAudioBackendException(RealtimeVoiceFailure.Persistence, cause = failure)
                }
            }
        }

        /** 取当前 Chatbot 会话的最新历史；原生实时重连时刷新上下文，不重复申请租约。 */
        suspend fun currentHistory(): List<Content> {
            resolve()
            val session = requireNotNull(sessions.getSession(key())) { "Conversation no longer exists" }
            return historyFrom(session)
        }

        fun enqueue(content: Content, isNativeRealtimeTranscript: Boolean = false) {
            pending.addLast(Event(
                author = if (content.role == Role.USER) AgentSessionIdentity.USER_AUTHOR else AgentSessionIdentity.CHATBOT_NAME,
                content = content,
                customMetadata = if (isNativeRealtimeTranscript) {
                    mapOf(NATIVE_REALTIME_TRANSCRIPT_METADATA_KEY to true)
                } else {
                    null
                },
            ))
        }

        /** 重试沿用原事件 ID，并先查落盘结果，覆盖写成功但返回失败的情况。 */
        suspend fun flush() {
            try {
                while (pending.isNotEmpty()) {
                    val session = requireNotNull(sessions.getSession(key())) { "Conversation no longer exists" }
                    val event = pending.first()
                    if (session.events.none { it.id == event.id }) sessions.appendEvent(session, event)
                    pending.removeFirst()
                    conversations.notifyConversationContentChanged(requireNotNull(conversationId))
                }
                sessions.flush(key())
                conversationId?.let { conversations.refreshConversation(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw VoiceAudioBackendException(RealtimeVoiceFailure.Persistence, cause = failure)
            }
        }

        fun release() {
            lease?.release()
            lease = null
        }

        private fun historyFrom(session: com.google.adk.kt.sessions.Session) = session.events.mapNotNull { event ->
            val content = event.content ?: return@mapNotNull null
            if (content.role != Role.USER && content.role != Role.MODEL) return@mapNotNull null
            val parts = content.parts.filter { it.thought != true && (it.text != null || it.fileData != null || it.inlineData != null) }
            if (parts.isEmpty()) null else Content(role = content.role, parts = parts)
        }.takeLast(64)

        private fun key() = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.CHATBOT_USER_ID, requireNotNull(conversationId))
    }
}
