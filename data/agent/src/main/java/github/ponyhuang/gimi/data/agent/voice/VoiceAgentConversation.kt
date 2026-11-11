package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.FunctionCall
import github.ponyhuang.gimi.data.agent.AgentChatExecution
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.data.agent.conversation.UserInputToolProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTaskCodec
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 聊天模式语音通话的完整 Agent 执行层：每轮把识别文字直接交给 chat agent
 * （与文字聊天共用 [AgentChatRunner] 的缓存运行时），把模型正文增量交给上层
 * 送入 TTS。历史与持久化全部由 ADK Runner 落在会话上，本层不再手工搬运事件。
 *
 * 工具权限按语音通话约定「完全批准」：需要确认的工具照常声明，确认请求直接
 * 自动放行；`get_user_choice` / `adk_request_input` 这类需要用户答复的挂起请求
 * 则暂停本轮，等用户下一句口头答复后原调用恢复。
 */
class VoiceAgentConversation @Inject constructor(
    private val runner: AgentChatRunner,
    private val sessions: SessionService,
    private val conversations: ConversationRepository,
    private val gate: AgentRuntimeGate,
) {
    /** 一次通话独占的租约与挂起输入状态；会话结束后丢弃。 */
    internal fun bind(conversationId: String, selection: ModelSelection): Call = Call(conversationId, selection)

    internal inner class Call(
        private val conversationId: String,
        private val selection: ModelSelection,
    ) {
        private val writes = Mutex()
        private var lease: AgentRunLease? = null
        private var pendingInput: UserInputRequest? = null
        private var ranTurn = false

        /** 进入通话：取会话租约并确认目标聊天仍存在；忙或缺失映射为可翻译失败。 */
        suspend fun acquire() = writes.withLock {
            if (lease != null) return@withLock
            lease = try {
                gate.acquire(AgentTaskSource.REALTIME_VOICE, conversationId, AgentTaskPhase.WAITING_FOR_INPUT)
            } catch (busy: AgentSessionBusyException) {
                throw VoiceAudioBackendException(RealtimeVoiceFailure.ConversationBusy, cause = busy)
            }
            try {
                requireNotNull(sessions.getSession(sessionKey())) { "Conversation no longer exists" }
            } catch (cancelled: CancellationException) {
                release()
                throw cancelled
            } catch (failure: Exception) {
                release()
                throw VoiceAudioBackendException(RealtimeVoiceFailure.Persistence, cause = failure)
            }
        }

        /**
         * 跑一轮：把 [text] 发给（或作为上一轮挂起输入请求的口头答复恢复）完整 Agent，
         * 模型正文增量交给 [onDelta]。需要确认的工具逐个自动批准后继续收集；
         * 遇到用户输入请求则结束本轮，等下一轮再恢复。
         */
        suspend fun runTurn(text: String, onDelta: suspend (String) -> Unit) {
            val paused = pendingInput
            pendingInput = null
            ranTurn = true
            lease?.updatePhase(AgentTaskPhase.GENERATING)
            try {
                var execution = createExecution()
                var events = if (paused != null) {
                    execution.respondToInputRequest(
                        callId = paused.callId,
                        toolName = paused.toolName,
                        payload = UserInputToolProtocol.responsePayload(paused.toolName, text),
                    )
                } else {
                    execution.send(text)
                }
                while (true) {
                    val confirmations = collectTurn(events, onDelta)
                    if (pendingInput != null || confirmations.isEmpty()) return
                    // 完全批准：语音模式不弹确认，逐个放行；收齐同批决策的那次恢复才带事件流。
                    for (callId in confirmations) {
                        events = execution.respondToToolConfirmation(callId, confirmed = true)
                    }
                }
            } finally {
                lease?.updatePhase(AgentTaskPhase.WAITING_FOR_INPUT)
                // 事件由 ADK Runner 落盘，会话仓储没有感知；逐轮通知刷新，聊天界面才能立即看到新消息。
                notifyContentChanged()
            }
        }

        /** 通话结束释放租约；本轮写过事件就刷新会话，半轮被打断的残留也要让聊天界面看到。 */
        suspend fun release() {
            lease?.release()
            lease = null
            if (ranTurn) notifyContentChanged()
        }

        private suspend fun notifyContentChanged() = withContext(NonCancellable) {
            try {
                conversations.notifyConversationContentChanged(conversationId)
                conversations.refreshConversation(conversationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w(TAG, "Voice conversation refresh failed", failure)
            }
        }

        private suspend fun createExecution(): AgentChatExecution = runner.createExecution(
            userId = AgentSessionIdentity.DEFAULT_USER_ID,
            sessionId = conversationId,
            selection = selection,
            // 确认类工具照常声明，运行期由 runTurn 自动批准，而不是从工具集排除。
            allowConfirmationRequiredTools = true,
            toolConfiguration = conversations.conversationToolConfiguration(conversationId),
            a2uiCapabilities = null,
            instructionSuffix = VOICE_CHAT_INSTRUCTION,
        )

        /**
         * 收集一轮事件流：归并模型正文增量、记录自动批准的确认调用与挂起的输入请求。
         * 错误事件已由执行句柄抛出；这里只把其余异常映射为可翻译的生成失败。
         */
        private suspend fun collectTurn(events: Flow<Event>, onDelta: suspend (String) -> Unit): List<String> {
            val confirmations = mutableListOf<String>()
            // 每轮模型输出（两次工具响应之间）先流式给出 partial 增量，再给一次合并全文；
            // 已有增量时跳过全文，避免回答在字幕里重复一遍。
            var hasPartial = false
            try {
                events.collect { event ->
                    if (event.author == A2uiGenerationTaskCodec.AUTHOR) return@collect
                    // 工具响应意味着下一轮模型输出开始，partial 状态按轮重置。
                    if (event.functionResponses().isNotEmpty()) hasPartial = false
                    if (event.author != AgentSessionIdentity.USER_AUTHOR) {
                        val body = event.content?.parts.orEmpty()
                            .filter { it.thought != true }.mapNotNull { it.text }.joinToString("")
                        if (body.isNotEmpty() && (event.partial || !hasPartial)) {
                            if (event.partial) hasPartial = true
                            onDelta(body)
                        }
                    }
                    for (call in event.functionCalls()) {
                        UserInputToolProtocol.parseRequest(call.id, call.name, call.args)?.let {
                            pendingInput = it
                        }
                        if (call.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
                            call.id?.let(confirmations::add)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // 语音界面只展示失败分类；这里留下完整异常线索供真机排查。
                android.util.Log.w(TAG, "Voice agent turn failed", failure)
                throw VoiceAudioBackendException(RealtimeVoiceFailure.Generation, cause = failure)
            }
            return confirmations
        }

        private fun sessionKey() = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, conversationId)
    }

    private companion object {
        private const val TAG = "VoiceAgentConversation"

        /** 追加到完整助手指令之后的语音场景约束：口语化人设 + TTS 输出底线 + 听不清时的确认策略 + 工具播报节奏。提示词统一用英文撰写（与主指令一致），回复语言由主指令的用户语言规则约束。后缀参与 Agent 缓存键，语音与文字聊天各持一份。 */
        const val VOICE_CHAT_INSTRUCTION = "\n\n<realtime_voice>" +
            "\n- You are talking with the user in a real-time voice call; your replies are read aloud by text-to-speech." +
            "\n- Speak like a real person: colloquial and warm; natural interjections such as \"okay\" and \"mm-hmm\" are fine. Answer simple questions in one or two sentences; when explaining or comparing, give the conclusion first, then the reasons; keep complex topics in flowing narration." +
            "\n- Speak only natural, conversational sentences; never use Markdown, lists, numbering, code blocks, parenthetical asides, emoji, or any other written formatting or symbols; when enumerating, connect naturally with \"first..., then...\"." +
            "\n- Ask only one question at a time; if you did not hear clearly or are unsure, confirm briefly in one short sentence, never repeat the same confirmation, and never guess." +
            "\n- Keep using tools as usual: before a call, announce briefly in one short sentence what you are about to do and vary the wording; after it completes, summarize the result first and then the next step without reciting raw data; if it fails, explain in plain words and offer another way to help, never read out error codes or technical details." +
            "\n</realtime_voice>"
    }
}
