package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** 轻聊共用普通聊天存储和写入租约，只恢复文字/附件，工具调用不会进入轻聊上下文。 */
class SimpleVoiceConversation @Inject constructor(
    private val sessions: SessionService,
    private val conversations: ConversationRepository,
    private val resolver: ConversationSessionResolver,
    private val gate: AgentRuntimeGate,
) {
    internal fun bind(conversationId: String?): Call = Call(conversationId)

    internal inner class Call(private var conversationId: String?) {
        private var lease: AgentRunLease? = null
        private val pending = ArrayDeque<Event>()

        suspend fun acquire(): List<Content> {
            if (lease != null) return emptyList()
            try {
                val id = conversationId ?: resolver.resolveCurrentOrCreate().sessionId.also { conversationId = it }
                lease = gate.acquire(AgentTaskSource.REALTIME_VOICE, id, AgentTaskPhase.WAITING_FOR_INPUT)
                val session = requireNotNull(sessions.getSession(key())) { "Conversation no longer exists" }
                return session.events.mapNotNull { event ->
                    val content = event.content ?: return@mapNotNull null
                    if (content.role != Role.USER && content.role != Role.MODEL) return@mapNotNull null
                    val parts = content.parts.filter { it.thought != true && (it.text != null || it.fileData != null || it.inlineData != null) }
                    if (parts.isEmpty()) null else Content(role = content.role, parts = parts)
                }.takeLast(64)
            } catch (failure: Exception) {
                release()
                when (failure) {
                    is CancellationException -> throw failure
                    is AgentSessionBusyException -> throw VoiceAudioBackendException(RealtimeVoiceFailure.ConversationBusy, cause = failure)
                    else -> throw VoiceAudioBackendException(RealtimeVoiceFailure.Persistence, cause = failure)
                }
            }
        }

        fun enqueue(content: Content) {
            pending.addLast(Event(
                author = if (content.role == Role.USER) AgentSessionIdentity.USER_AUTHOR else AgentSessionIdentity.ASSISTANT_NAME,
                content = content,
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

        private fun key() = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, requireNotNull(conversationId))
    }
}
