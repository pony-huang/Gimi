package github.ponyhuang.asssistantai.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.service.AppFunction
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Part
import dagger.Lazy
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
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
 * 让 system agent / 系统级 intent 直接调用的三件 AppFunction 容器 —
 * `listModelServices`、`setCurrentModel`、`sendMessage`。
 *
 * 设计要点：
 * - 通过 Hilt `@Inject` 复用现有 `AgentChatRunner` 单例（`di/AgentModule.kt`），
 *   不与 ChatViewModel 抢状态。
 * - 与 [ModelServiceRepository] 共用同一份进程级配置，
 *   AppFunction 调 `setCurrentModel` 之后用户下次打开 UI 也会立即看到模型切换。
 * - 所有 `@AppFunction` 函数体首句 `withContext(Dispatchers.IO)`，与
 *   `appfunctions` skill 的硬约束一致（AppFunction runtime 默认在 UI 线程上调用）。
 * - `sendMessage` 把 streaming `Flow<Event>` 收敛为单字符串返回：只取最后那段
 *   `partial=false` 事件的 `content.parts[*].text`，屏蔽打字机细节。
 *
 * 参考：`E:/workplace/appfunctions/ChatApp/shared/.../AppFunctions.kt`（Google 官方样例）。
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
        private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    ) {

        /**
         * 列出当前 store 中全部模型服务（含禁用项），每个服务附带其下所有 modelId。
         *
         * Operational pattern:
         * - 调用方拿到结果后，应根据用户的实际意图挑选一个 `serviceId + modelId`。
         * - `availableModels` 中的字符串是原始 modelId，需要配合 `setCurrentModel`
         *   或 `sendMessage` 使用。
         *
         * 不修改 store，不发出网络请求。始终返回非 null 列表（store 为空时为空列表）。
         *
         * @param appFunctionContext 调用上下文；本函数未使用，但运行时要求首参。
         * @return 全量 `ModelServiceSummary` 列表，按 store 的存储顺序。
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun listModelServices(
            appFunctionContext: AppFunctionContext,
        ): List<ModelServiceSummary> = withContext(Dispatchers.IO) {
            modelServices.awaitReady()
            modelServices.services.value.map { provider ->
                ModelServiceSummary(
                    serviceId = provider.serviceId,
                    serviceName = provider.serviceName,
                    availableModels = provider.LLMModelGroups.flatMap { group ->
                        group.models.map { it.modelId }
                    },
                )
            }
        }

        /**
         * 切换当前激活模型，让 `sendMessage` 下一次起使用新模型。
         *
         * Operational pattern:
         * - 调用前通常需要先调 `listModelServices` 拿到合法的 `serviceId / groupId / modelId`。
         * - 即使目标服务当前 `isEnabled=false`，本函数仍写入选择；调用方需自行保证后续
         *   `sendMessage` 时服务可用，否则会抛 `AppFunctionAppUnknownException`。
         *
         * 不会中断正在进行的 `sendMessage` 流式会话 — 当前那一轮仍使用旧模型。
         *
         * @param appFunctionContext 调用上下文；本函数未使用，但运行时要求首参。
         * @param serviceId 目标服务 ID（与 `ModelProvider.serviceId` 一致）。
         * @param groupId 目标模型所属组 ID（与 `ModelGroup.groupId` 一致）。
         * @param modelId 目标模型 ID（与 `ModelItem.modelId` 一致）。
         * @throws AppFunctionElementNotFoundException 当 serviceId/groupId/modelId 中
         *   任意一项不存在于 store 时。
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun setCurrentModel(
            appFunctionContext: AppFunctionContext,
            serviceId: String,
            groupId: String,
            modelId: String,
        ) = withContext(Dispatchers.IO) {
            modelServices.awaitReady()
            val provider = modelServices.getService(serviceId)
                ?: throw AppFunctionElementNotFoundException(
                    "serviceId '$serviceId' not found",
                )
            val group = provider.LLMModelGroups.firstOrNull { it.groupId == groupId }
                ?: throw AppFunctionElementNotFoundException(
                    "groupId '$groupId' not found in service '$serviceId'",
                )
            val model = group.models.firstOrNull { it.modelId == modelId }
                ?: throw AppFunctionElementNotFoundException(
                    "modelId '$modelId' not found in service '$serviceId' group '$groupId'",
                )

            // 仅在 service / group / model 三元组全部命中后才落盘 + 重建 runner；
            // 出错时 recreate 不应触发，保持 store 的单 state-of-truth 假设。
            when (runWhenAgentIdle {
                modelServices.setCurrentSelection(
                    LLMModelSelection(
                        serviceId = provider.serviceId,
                        groupId = group.groupId,
                        modelId = model.modelId,
                    ),
                )
                agentChatRunnerLazy.get().recreate()
            }) {
                is AgentMutationResult.Applied -> Unit
                AgentMutationResult.BlockedByActiveAgent -> throw AppFunctionAppUnknownException(
                    "Agent task is running; stop it before changing the model",
                )
            }
        }

        /**
         * 把单条文本消息发给当前激活模型，并返回该轮 LLM 的最终回答文本。
         *
         * Operational pattern:
         * - 调用方传入完整 prompt；不要尝试把多轮对话拼成一条 message —
         *   本函数每次调用都新建 ADK session ID（即每次都是"全新会话"）。
         * - 内部硬性 60 秒超时；超时后抛 `AppFunctionAppUnknownException`，调用方需自行重试。
         *
         * Event 流收敛规则：取最后一段 `partial == false` 的 Event，从其
         * `content.parts[*]` 取全部 `Part.text` 拼接成单字符串返回。若整段流结束
         * 都没有 `partial=false` 的 Event，返回空字符串。
         *
         * @param appFunctionContext 调用上下文；本函数未使用，但运行时要求首参。
         * @param message 不可为 blank 的用户文本消息。
         * @return LLM 生成的最终文本（屏蔽 streaming 细节，单字符串）。
         * @throws AppFunctionInvalidArgumentException 当 `message` 为 blank 时。
         * @throws AppFunctionAppUnknownException 当 store 中无启用服务 / 网络失败 /
         *   LLM 在 60 秒内未完成 / 任意未分类异常时。
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun sendMessage(
            appFunctionContext: AppFunctionContext,
            message: String,
        ): String = withContext(Dispatchers.IO) {
            modelServices.awaitReady()
            if (message.isBlank()) {
                throw AppFunctionInvalidArgumentException("message must not be blank")
            }
            if (modelServices.services.value.none { it.isEnabled }) {
                throw AppFunctionAppUnknownException("No enabled model service available")
            }

            // 每次 AppFunction 调用都开新会话 — 避免 session 长尾污染与"上次调用遗留状态"风险。
            val sessionId = UUID.randomUUID().toString()
            val lease = agentRuntimeGate.acquire(AgentTaskSource.APP_FUNCTION)

            val collectedEvents: List<Event>? = try {
                withTimeoutOrNull(TIMEOUT_SECONDS.seconds) {
                    agentChatRunnerLazy
                        .get()
                        .send(
                            userId = USER_ID,
                            sessionId = sessionId,
                            text = message
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
            collectedEvents.finalAssistantText()
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
