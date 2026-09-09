package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.apps.App
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.events.Event
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.plugins.LoggingPlugin
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.summarizer.EventsCompactionConfig
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatRunner.Companion.MAX_CACHED_RUNTIMES
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.data.agent.di.AgentModule
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ChatSessionRewindException
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * Agent 聊天运行器 — 把 ADK `InMemoryRunner.runAsync(...)` 封装为 `Flow<Event>`。
 *
 * 设计要点：
 * - ADK Runner 本身不持有会话状态（历史、恢复点全部落在 [SessionService] 的 Session
 *   事件里），因此相同构建配置的会话可以安全共享同一个 Agent/Runner。
 * - 运行时按 [AgentKey]（模型选择 + [ToolAccessMode] + 外部配置版本）做 LRU 缓存
 *   （上限 [MAX_CACHED_RUNTIMES]），不同会话只要配置相同就复用同一份昂贵构建产物
 *   （模型客户端、MCP 解析结果等），不再每会话各建一个 Agent。
 * - 会话级工具勾选、确认工具开关通过 `RunConfig.customMetadata`（[ToolRunMetadata]）
 *   按请求透传给各 Toolset 自行过滤，均不参与缓存键 —— 切换勾选或确认开关不会触发
 *   Agent 重建。
 * - 每轮返回显式 [Execution]，直接持有 runner 和配置；缓存淘汰不影响等待中的恢复。
 * - 构造期由 [AgentModule] 通过 Hilt 注入 [sessionService]、[artifactService]、[plugins]
 *   及 [configuration]；不持有 in-memory 默认实现。
 * - [factory] 仅在缓存未命中时按需调用，保证模型/访问模式切换立即生效。当前正在
 *   `runAsync` 中的会话不受影响（[createExecution] 入口处已快照 runner 引用）。
 * - 不对 `Event` 做任何加工；Event → UI 渲染的合并工作由 `ChatViewModel` 的 reducer 完成。
 * - `runConfig` 默认开启 SSE 流式，便于 UI 端做打字机效果。
 *
 * 线程模型：缓存由 [runnerMutex] 保护，所有调用都通过协程 `Flow` 完成。
 */
class AgentChatRunner(
    private val factory: suspend (AgentBuildSpec) -> AgentRuntime,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService?,
    private val memoryService: MemoryService ?,
    private val configuration: () -> AgentBuildConfigurationSnapshot = {
        AgentBuildConfigurationSnapshot(
            revision = Unit,
            pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()),
        )
    },
    private val plugins: (PluginRuntimeSnapshot<AgentPlugin>) -> List<Plugin> = { emptyList() },
    private val toolAccessRepository: ToolAccessRepository,
) {
    /**
     * Agent 构建的唯一缓存键。
     *
     * @property selection 构建 Agent 时使用的显式模型选择（模型名称）。
     * @property toolAccessMode 工具声明加载模式。
     * @property reasoningEffort 当前会话的推理强度。
     * @property revision 构建时的外部配置版本（各贡献方 revision 的组合，
     *   含工具授权/MCP/模型目录/插件运行时）。
     */
    private data class AgentKey(
        val selection: ModelSelection?,
        val toolAccessMode: ToolAccessMode,
        val reasoningEffort: ReasoningEffort,
        val revision: Any,
    )

    /**
     * 同一 [AgentKey] 下所有会话共享的运行时。
     *
     * @property modelRuntime 不含凭据的模型运行信息。
     * @property runner 可跨会话共享的 ADK Runner（会话状态在 SessionService 中）。
     */
    private class SharedRuntime(
        val modelRuntime: ModelRuntimeMetadata,
        val runner: InMemoryRunner,
    )

    private val runtimes = LinkedHashMap<AgentKey, SharedRuntime>(16, 0.75f, true)
    private val runnerMutex = Mutex()

    private fun buildRunner(
        agent: BaseAgent,
        pluginRuntime: PluginRuntimeSnapshot<AgentPlugin>,
    ): InMemoryRunner = InMemoryRunner(
        app = App(
            appName = APP_NAME,
            plugins = mutableListOf(LoggingPlugin()) + plugins(pluginRuntime),
            rootAgent = agent,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
            // 对话摘要压缩
            eventsCompactionConfig = EventsCompactionConfig()
        ),
        sessionService = sessionService,
        artifactService = artifactService,
        memoryService = memoryService
    )

    /** 固定一轮的构建配置和工具元数据；调用方持有句柄直到完成或取消。 */
    suspend fun createExecution(
        userId: String,
        sessionId: String,
        selection: ModelSelection? = null,
        allowConfirmationRequiredTools: Boolean = true,
        toolConfiguration: ConversationToolConfiguration? = null,
    ): Execution {
        val toolAccessMode = toolAccessRepository.defaultToolAccessMode.value
        val reasoningEffort = toolConfiguration?.reasoningEffort ?: ReasoningEffort.MEDIUM
        val buildConfiguration = configuration()
        val key = AgentKey(selection, toolAccessMode, reasoningEffort, buildConfiguration.revision)
        return runnerMutex.withLock {
            val runtime = runtimes[key] ?: factory(
                AgentBuildSpec(
                    selection = selection,
                    toolAccessMode = toolAccessMode,
                    reasoningEffort = reasoningEffort,
                    pluginRuntime = buildConfiguration.pluginRuntime,
                ),
            ).let { agentRuntime ->
                SharedRuntime(
                    modelRuntime = agentRuntime.modelRuntime,
                    runner = buildRunner(agentRuntime.agent, buildConfiguration.pluginRuntime),
                ).also { runtimes[key] = it }
            }
            while (runtimes.size > MAX_CACHED_RUNTIMES) {
                runtimes.remove(runtimes.entries.first().key)
            }
            val metadata = ToolRunMetadata.of(
                modelRuntime = runtime.modelRuntime,
                toolConfiguration = toolConfiguration,
                allowConfirmationRequiredTools = allowConfirmationRequiredTools,
            )
            Execution(userId, sessionId, runtime.runner, metadata)
        }
    }

    /** 单轮执行句柄，恢复只使用创建时的 Runner 与元数据，不访问全局配置或 LRU。 */
    class Execution internal constructor(
        private val userId: String,
        private val sessionId: String,
        private val runner: InMemoryRunner,
        private val customMetadata: Map<String, Any>,
    ) {
        /** 把新用户消息发送给本轮 Agent。 */
        suspend fun send(
            text: String,
            fileAttachments: List<FileAttachment> = emptyList(),
            rewindBeforeInvocationId: String? = null,
        ): Flow<Event> {
            val parts = buildList {
                text.takeIf(String::isNotBlank)?.let { add(Part(text = it)) }
                fileAttachments.forEach { attachment ->
                    add(
                        Part(
                            fileData = FileData(
                                mimeType = attachment.mimeType,
                                displayName = attachment.displayName,
                                fileUri = requireNotNull(attachment.payloadReference) {
                                    "Managed attachment reference is missing"
                                },
                            ),
                        ),
                    )
                }
                attachmentPathManifest(fileAttachments)?.let { add(Part(text = it)) }
            }
            val newMessage = Content(
                role = Role.USER,
                parts = parts,
            )
            rewindBeforeInvocationId?.let { rewindInvocationId ->
                try {
                    runner.rewindAsync(userId, sessionId, rewindInvocationId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    // 回滚失败发生在 runAsync 之前，不能把尚未创建的新 invocation 当成下次回滚点。
                    throw ChatSessionRewindException(failure)
                }
            }
            return runner.runAsync(
                userId = userId,
                sessionId = sessionId,
                // ADK 0.8.0 在 isResumable=true 下：非空 invocationId 会被当成"恢复既有 invocation"，
                // 恢复分支要求 session 已有事件；全新会话首条消息会因此抛 "Session ... has no events to resume"。
                // 新消息必须传 null 让 ADK 自建 invocation；真实 id 由事件回流携带，用作失败轮回退边界。
                invocationId = null,
                newMessage = newMessage,
                stateDelta = null,
                runConfig = RunConfig(
                    streamingMode = StreamingMode.SSE,
                    customMetadata = customMetadata,
                ),
            ).flowOn(Dispatchers.IO)
        }

        /**
         * 恢复暂停的 ADK 工具确认请求。
         *
         * @param confirmationCallId 工具确认的调用 ID
         * @param confirmed 用户是否确认
         * @return Event 流
         */
        suspend fun respondToToolConfirmation(
            confirmationCallId: String,
            confirmed: Boolean,
        ): Flow<Event> = resumeWithFunctionResponse(
            response = FunctionResponse(
                name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                id = confirmationCallId,
                response = mapOf("confirmed" to confirmed),
            ),
        )

        /**
         * 用用户答复恢复挂起的用户输入请求（`adk_request_input` / `get_user_choice`）。
         *
         * @param callId 挂起的 function call ID
         * @param toolName 触发挂起的工具名（决定 FunctionResponse.name，需与挂起调用一致）
         * @param payload 答复负载（键约定见 `UserInputToolProtocol`）
         * @return Event 流
         */
        suspend fun respondToInputRequest(
            callId: String,
            toolName: String,
            payload: Map<String, Any?>,
        ): Flow<Event> = resumeWithFunctionResponse(
            response = FunctionResponse(
                name = toolName,
                id = callId,
                response = payload,
            ),
        )

        /**
         * 以 role=user 的 `FunctionResponse` 新消息恢复暂停的 invocation。
         *
         * ADK 靠"响应 id 覆盖挂起的长时运行调用 id"判定这是恢复而非新的暂停，
         * 恢复时挂起工具不重跑，响应直接作为调用结果进入模型上下文。
         */
        private suspend fun resumeWithFunctionResponse(
            response: FunctionResponse,
        ): Flow<Event> {
            val resumeMessage = Content(
                role = Role.USER,
                parts = listOf(Part(functionResponse = response)),
            )
            return runner.runAsync(
                userId = userId,
                sessionId = sessionId,
                invocationId = null,
                newMessage = resumeMessage,
                stateDelta = null,
                // 恢复调用沿用最近一次 send 的工具配置，保证 Toolset 过滤上下文一致。
                runConfig = RunConfig(
                    streamingMode = StreamingMode.SSE,
                    customMetadata = customMetadata,
                ),
            ).flowOn(Dispatchers.IO)
        }
    }

    companion object {
        const val APP_NAME: String = AgentSessionIdentity.APP_NAME

        /** 共享运行时（Agent + Runner）的 LRU 上限，按 [AgentKey] 计数。 */
        const val MAX_CACHED_RUNTIMES: Int = 10
    }
}

/**
 * 列出每个附件在磁盘上的位置，供模型转填给吃路径的工具。
 *
 * 模型适配层把附件转成 image/document content part 时会丢掉路径（见 `Openai.buildFilePart`），
 * 于是像小红书 `publish_content.images` 这种要"本地绝对路径"的参数就无从填写。这里补一段清单
 * 把路径带进上下文。没有可描述的附件时返回 null。
 */
internal fun attachmentPathManifest(attachments: List<FileAttachment>): String? {
    val referenced = attachments.mapNotNull { attachment ->
        attachment.payloadReference?.let { path -> attachment.displayName to path }
    }
    if (referenced.isEmpty()) return null
    return buildString {
        append("<attachments>\n")
        append("The user attached ${referenced.size} file(s). ")
        append("Tools that take a local file path can use these paths as-is.")
        referenced.forEachIndexed { index, (displayName, path) ->
            append("\n${index + 1}. ")
            if (displayName.isNotBlank()) append("$displayName — ")
            append(path)
        }
        append("\n</attachments>")
    }
}
