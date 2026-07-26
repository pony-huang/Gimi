package github.ponyhuang.asssistantai.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.service.AppFunction
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Part
import dagger.Lazy
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
 * - 会话语义对齐"语音弹窗"（Assistant Overlay）：调用方传入 `sessionId` 即复用其
 *   历史；传空则新建一个会话，并把实际使用的 sessionId 通过 [AssistantReply] 返回，
 *   调用方下一轮回传即可延续对话。
 * - `userId` 仅作会话归属标识透传给 ADK；传空回退到默认单用户 [USER_ID]。
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
        private val modelServices: ModelServiceRepository,
        private val agentRuntimeGate: AgentRuntimeGate,
    ) {

        /**
         * Send a text message to the app's AI agent and return the agent's final reply.
         *
         * 会话语义（自动创建或复用当前会话）：
         * - `sessionId` 非空：复用该会话，本轮携带其历史，可实现多轮对话。
         * - `sessionId` 为空：新建一个会话；实际使用的新 id 会通过返回值
         *   [AssistantReply.sessionId] 给出，调用方下一轮回传即可延续对话。
         *
         * `userId` 仅作会话归属标识透传给底层运行器；为空时回退到默认单用户。
         * 相同的 `(userId, sessionId)` 才会命中同一段历史，请成对复用。
         *
         * 内部硬性 60 秒超时；超时后抛 [AppFunctionAppUnknownException]，调用方需自行重试。
         * Event 流收敛规则：取最后一段 `partial == false` 的 Event，从其
         * `content.parts[*]` 取全部 `Part.text` 拼接成单字符串；若整段流结束都没有
         * `partial=false` 的 Event，[AssistantReply.reply] 为空字符串。
         *
         * @param appFunctionContext 调用上下文；本函数未使用，但运行时要求首参。
         * @param userId 调用方的用户标识；为空时使用默认单用户。
         * @param sessionId 目标会话 id；为空表示新建会话，实际 id 见返回值。
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
            userId: String,
            sessionId: String,
            message: String,
        ): AssistantReply = withContext(Dispatchers.IO) {
            modelServices.awaitReady()
            if (message.isBlank()) {
                throw AppFunctionInvalidArgumentException("message must not be blank")
            }
            if (modelServices.services.value.none { it.isEnabled }) {
                throw AppFunctionAppUnknownException("No enabled model service available")
            }

            val effectiveUserId = userId.ifBlank { USER_ID }
            // 传空 → 新建会话；非空 → 复用调用方指定的会话（延续历史）。
            val effectiveSessionId = sessionId.ifBlank { UUID.randomUUID().toString() }

            val lease = agentRuntimeGate.acquire(AgentTaskSource.APP_FUNCTION, effectiveSessionId)

            val collectedEvents: List<Event>? = try {
                withTimeoutOrNull(TIMEOUT_SECONDS.seconds) {
                    agentChatRunnerLazy
                        .get()
                        .send(
                            userId = effectiveUserId,
                            sessionId = effectiveSessionId,
                            text = message,
                        )
                        .flowOn(Dispatchers.IO)
                        .toList()
                }
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
            }

            if (collectedEvents == null) {
                throw AppFunctionAppUnknownException(
                    "LLM request timed out after $TIMEOUT_SECONDS s",
                )
            }
            AssistantReply(
                sessionId = effectiveSessionId,
                reply = collectedEvents.finalAssistantText(),
            )
        }

        private companion object {
            // 与 `di/AgentModule.kt::USER_ID` 保持一致 — 此处复制一份常量以避免
            // 把 AgentModule 的私有字段升级为 public；如未来需要修改请同步两处。
            const val USER_ID: String = "user-default"
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
