package github.ponyhuang.gimi.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.service.AppFunction
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Part
import dagger.Lazy
import github.ponyhuang.gimi.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 面向外部 agent 的 AppFunction 入口 —— 只暴露一个 [sendMessage]。
 *
 * 设计要点：
 * - 通过 Hilt `@Inject` 复用现有 `AgentChatRunner` 单例（`di/AgentModule.kt`），
 *   不与 ChatViewModel 抢状态；底层直连 runner，不经过语音弹窗的
 *   `AssistantSessionCoordinator`（无语音/TTS/工具确认等额外链路）。
 * - 只接受本应用签发且尚未过期的 `sessionId`；传空时签发新句柄。调用方不能选择
 *   ADK `userId`，也不能用任意 id 读取应用内其他会话历史。
 * - `@AppFunction` 函数体首句 `withContext(Dispatchers.IO)`，与 `appfunctions`
 *   skill 的硬约束一致（AppFunction runtime 默认在 UI 线程上调用）。
 * - 把 streaming `Flow<Event>` 收敛为单字符串：只取最后那段 `partial=false`
 *   事件的 `content.parts[*].text`，屏蔽打字机细节。
 */
class AssistantFunctions
    @Inject
    constructor(
        // 必须用 `dagger.Lazy<...>` 而非直接注入 `AgentChatRunner` — Hilt 在
        // `AsssistantaiApp.onCreate` 阶段会 eager-求值所有 `@Singleton` 依赖，
        // 仓库在首次注入时完成种子和缓存恢复。保留 Lazy，避免 AppFunction 配置阶段
        // 提前构造网络运行器。
        private val agentChatRunnerLazy: Lazy<AgentChatRunner>,
        private val modelServices: AgentModelConfigurationSource,
        private val agentRuntimeGate: AgentRuntimeGate,
        private val managedSessions: ManagedSessionRegistry,
        private val sessionService: SessionService,
    ) {

        /**
         * Send a text message to the app's AI agent and return the agent's final reply.
         *
         * 会话语义（自动创建或复用当前会话）：
         * - `sessionId` 非空：复用该会话，本轮携带其历史，可实现多轮对话。
         * - `sessionId` 为空：新建一个会话；实际使用的新 id 会通过返回值
         *   [AssistantReply.sessionId] 给出，调用方下一轮回传即可延续对话。
         *
         * 内部硬性 60 秒超时；超时后抛 [AppFunctionAppUnknownException]，调用方需自行重试。
         * Event 流收敛规则：取最后一段 `partial == false` 的 Event，从其
         * `content.parts[*]` 取全部 `Part.text` 拼接成单字符串；若整段流结束都没有
         * `partial=false` 的 Event，[AssistantReply.reply] 为空字符串。
         *
         * @param appFunctionContext 调用上下文；本函数未使用，但运行时要求首参。
         * @param sessionId 本应用此前返回的会话句柄；为空表示新建受管会话。
         * @param message 不可为 blank 的用户文本消息。
         * @return 本轮实际会话 id 与 LLM 最终回答文本，见 [AssistantReply]。
         * @throws AppFunctionInvalidArgumentException 当 `message` 为 blank 时；请补全非空文本后重试。
         * @throws AppFunctionAppUnknownException 当 store 中无启用服务（此时请引导用户在 UI
         *   启用某个模型服务）/ 网络失败 / LLM 在 60 秒内未完成（可重试或换更快模型）/
         *   任意未分类异常时。
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun sendMessage(
            appFunctionContext: AppFunctionContext,
            sessionId: String,
            message: String,
        ): AssistantReply = withContext(Dispatchers.IO) {
            // TODO(appfunctions): Agent 工具配置改为 invocation context 透传后，
            //  该入口的语义（无 UI 确认、无会话工具配置）尚未重新验证，
            //  按需求暂时停用；恢复时移除本 throw 并还原下方注释掉的实现。
            throw AppFunctionAppUnknownException(
                "Assistant AppFunction is temporarily unavailable.",
            )
            /*
            modelServices.awaitReady()
            if (message.isBlank()) {
                throw AppFunctionInvalidArgumentException("message must not be blank")
            }
            if (modelServices.currentServices().none { it.isEnabled }) {
                throw AppFunctionAppUnknownException("No enabled model service available")
            }

            val managedSession = managedSessions.resolve(sessionId)
                ?: throw AppFunctionInvalidArgumentException(
                    "sessionId is unknown or expired; pass an empty sessionId to start a new session",
                )
            val effectiveSessionId = managedSession.sessionId

            val lease = agentRuntimeGate.acquire(AgentTaskSource.APP_FUNCTION, effectiveSessionId)
            var completed = false
            val collectedEvents: List<Event>? = try {
                val events = withTimeoutOrNull(TIMEOUT_SECONDS.seconds) {
                    agentChatRunnerLazy
                        .get()
                        .send(
                            userId = USER_ID,
                            sessionId = effectiveSessionId,
                            text = message,
                            allowConfirmationRequiredTools = false,
                        )
                        .flowOn(Dispatchers.IO)
                        .toList()
                }
                completed = events != null && events.none {
                    it.errorCode != null || !it.errorMessage.isNullOrBlank()
                }
                events
            } catch (cancel: CancellationException) {
                // 不吞协程取消 — 让框架正常向上传播。
                throw cancel
            } catch (af: AppFunctionException) {
                // 已经是有意义的 AppFunction 异常，直接冒泡。
                throw af
            } catch (t: Throwable) {
                throw AppFunctionAppUnknownException(
                    "Failed to send message: ${t.message ?: t::class.java.simpleName}",
                )
            } finally {
                lease.release()
                if (managedSession.isNew && !completed) {
                    managedSessions.revoke(effectiveSessionId)
                    withContext(NonCancellable) {
                        agentChatRunnerLazy.get().releaseSession(effectiveSessionId)
                        runCatching {
                            sessionService.deleteSession(
                                SessionKey(
                                    appName = AgentChatRunner.APP_NAME,
                                    userId = USER_ID,
                                    id = effectiveSessionId,
                                ),
                            )
                        }
                    }
                }
            }

            if (collectedEvents == null) {
                throw AppFunctionAppUnknownException(
                    "LLM request timed out after $TIMEOUT_SECONDS s",
                )
            }
            collectedEvents.lastOrNull {
                it.errorCode != null || !it.errorMessage.isNullOrBlank()
            }?.let { errorEvent ->
                throw AppFunctionAppUnknownException(
                    errorEvent.errorMessage ?: errorEvent.errorCode ?: "Unknown agent error",
                )
            }
            AssistantReply(
                sessionId = effectiveSessionId,
                reply = collectedEvents.finalAssistantText(),
            )
            */
        }

        private companion object {
            const val USER_ID: String = AgentSessionIdentity.DEFAULT_USER_ID
            const val TIMEOUT_SECONDS: Long = 60
        }
    }

/**
 * 从一组 Event 中提取"最后一段非 partial 的 assistant 文本"。
 *
 * 注意：ADK `Event` 的 `content.parts[*].text` 已经携带该次事件内的累积文本 — 不需要
 * 把所有 `partial=true` 事件再做归并（合并反而会导致重复文本）。
 */
private fun List<Event>.finalAssistantText(): String {
    val finalEvent = lastOrNull { !it.partial } ?: return ""
    val parts: List<Part> = finalEvent.content?.parts.orEmpty()
    return parts
        .mapNotNull { it.text }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "")
}
