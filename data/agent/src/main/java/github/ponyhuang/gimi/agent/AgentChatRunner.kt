package github.ponyhuang.gimi.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.apps.App
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.events.Event
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
import github.ponyhuang.gimi.agent.AgentChatRunner.Companion.MAX_CACHED_RUNTIMES
import github.ponyhuang.gimi.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.di.AgentModule
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
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
 * - 每个会话仅保存轻量 [SessionBinding]（最近使用的 key + metadata），供
 *   [respondToToolConfirmation] 恢复暂停的调用时复用。
 * - 构造期由 [AgentModule] 通过 Hilt 注入 [sessionService]、[artifactService]、[plugins]
 *   及 [configurationRevision]；不持有 in-memory 默认实现。
 * - [factory] 仅在缓存未命中时按需调用，保证模型/访问模式切换立即生效。当前正在
 *   `runAsync` 中的会话不受影响（[send] 入口处已快照 runner 引用）。
 * - 不对 `Event` 做任何加工；Event → UI 渲染的合并工作由 `ChatViewModel` 的 reducer 完成。
 * - `runConfig` 默认开启 SSE 流式，便于 UI 端做打字机效果。
 *
 * 线程模型：缓存由 [runnerMutex] 保护，所有调用都通过协程 `Flow` 完成。
 */
class AgentChatRunner(
    private val factory: suspend (
        ModelSelection?,
        ToolAccessMode,
    ) -> AgentRuntime,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService?,
    private val configurationRevision: () -> Any = { Unit },
    private val plugins: () -> List<Plugin> = { emptyList() }
) {
    /**
     * Agent 构建的唯一缓存键。
     *
     * @property selection 构建 Agent 时使用的显式模型选择（模型名称）。
     * @property toolAccessMode 工具声明加载模式。
     * @property revision 构建时的外部配置版本（工具授权/MCP/模型目录）。
     */
    private data class AgentKey(
        val selection: ModelSelection?,
        val toolAccessMode: ToolAccessMode,
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

    /**
     * 会话的轻量绑定：最近一轮使用的缓存键与 RunConfig metadata。
     *
     * @property key 最近一轮使用的 [AgentKey]。
     * @property customMetadata 最近一次请求的 RunConfig metadata，供确认恢复复用。
     */
    private data class SessionBinding(
        val key: AgentKey,
        val customMetadata: Map<String, Any>,
    )

    /** 一次 send/resume 的执行快照：共享 runner + 本会话 metadata。 */
    private data class ActiveTurn(
        val runner: InMemoryRunner,
        val customMetadata: Map<String, Any>,
    )

    private val runtimes = LinkedHashMap<AgentKey, SharedRuntime>(16, 0.75f, true)
    private val sessionBindings = LinkedHashMap<String, SessionBinding>(16, 0.75f, true)
    private val runnerMutex = Mutex()

    private fun buildRunner(agent: BaseAgent): InMemoryRunner = InMemoryRunner(
        app = App(
            appName = APP_NAME,
            plugins = mutableListOf(LoggingPlugin()) + plugins(),
            rootAgent = agent,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
            // 对话摘要压缩
            eventsCompactionConfig = EventsCompactionConfig()
        ),
        sessionService = sessionService,
        artifactService = artifactService,
    )

    /**
     * 清空所有缓存的运行时与会话绑定。
     *
     * 下次 [send] 时 [factory] 会被重新调用以使用最新的模型/工具配置构建 agent。
     * 当前正在 `runAsync` 中的会话不受影响。
     */
    suspend fun recreate() {
        runnerMutex.withLock {
            runtimes.clear()
            sessionBindings.clear()
        }
    }

    /**
     * 清空所有缓存的运行时与会话绑定，不创建替代。
     *
     * 当没有可用模型时调用，确保已禁用或删除的模型不会被后续请求使用。
     */
    suspend fun invalidate() {
        runnerMutex.withLock {
            runtimes.clear()
            sessionBindings.clear()
        }
    }

    /**
     * 释放指定会话的绑定。
     *
     * 共享运行时可能仍被其它会话使用，不在此处移除，由 LRU 自行淘汰。
     *
     * @param sessionId 要释放的会话 ID
     */
    suspend fun releaseSession(sessionId: String) {
        runnerMutex.withLock { sessionBindings.remove(sessionId) }
    }

    /**
     * 把用户输入发送给 Agent，返回 Event 流。
     *
     * @param userId 用户 ID（用于会话归属）
     * @param sessionId 会话 ID（同会话的历史会一并送入 LLM）
     * @param selection 模型选择；为 null 时使用默认模型
     * @param text 用户输入文本（可为空，仅当包含图片时）
     * @param fileAttachments 文件附件，作为 ADK inline data 传给模型
     * @param allowConfirmationRequiredTools 是否允许需要用户确认的工具调用
     * @param toolConfiguration 会话工具配置（启用的工具/MCP 服务器列表）；
     *   经 `RunConfig.customMetadata` 透传，由各 Toolset 按请求过滤
     * @return Event 流，通过 SSE 流式输出
     */
    suspend fun send(
        userId: String,
        sessionId: String,
        selection: ModelSelection? = null,
        text: String,
        fileAttachments: List<FileAttachment> = emptyList(),
        allowConfirmationRequiredTools: Boolean = true,
        toolConfiguration: ConversationToolConfiguration? = null,
    ): Flow<Event> {
        val activeTurn = currentTurnForNewMessage(
            sessionId,
            selection,
            toolConfiguration?.toolAccessMode ?: ToolAccessMode.ALWAYS_AVAILABLE,
            allowConfirmationRequiredTools,
            toolConfiguration,
        )
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
        return activeTurn.runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = newMessage,
            stateDelta = null,
            runConfig = RunConfig(
                streamingMode = StreamingMode.SSE,
                customMetadata = activeTurn.customMetadata,
            ),
        ).flowOn(Dispatchers.IO)
    }

    /**
     * 恢复暂停的 ADK 工具确认请求。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param confirmationCallId 工具确认的调用 ID
     * @param confirmed 用户是否确认
     * @return Event 流
     */
    suspend fun respondToToolConfirmation(
        userId: String,
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<Event> {
        val activeTurn = currentTurnForResume(sessionId)
        val confirmationResponse = Content(
            role = Role.USER,
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                        id = confirmationCallId,
                        response = mapOf("confirmed" to confirmed),
                    ),
                ),
            ),
        )
        return activeTurn.runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = confirmationResponse,
            stateDelta = null,
            // 恢复调用沿用最近一次 send 的工具配置，保证 Toolset 过滤上下文一致。
            runConfig = RunConfig(
                streamingMode = StreamingMode.SSE,
                customMetadata = activeTurn.customMetadata,
            ),
        ).flowOn(Dispatchers.IO)
    }

    private suspend fun currentTurnForNewMessage(
        sessionId: String,
        selection: ModelSelection?,
        toolAccessMode: ToolAccessMode,
        allowConfirmationRequiredTools: Boolean,
        toolConfiguration: ConversationToolConfiguration?,
    ): ActiveTurn {
        val key = AgentKey(selection, toolAccessMode, configurationRevision())
        return runnerMutex.withLock {
            val runtime = runtimes[key] ?: factory(selection, toolAccessMode).let { agentRuntime ->
                SharedRuntime(
                    modelRuntime = agentRuntime.modelRuntime,
                    runner = buildRunner(agentRuntime.agent),
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
            sessionBindings[sessionId] = SessionBinding(key, metadata)
            while (sessionBindings.size > MAX_SESSION_BINDINGS) {
                sessionBindings.remove(sessionBindings.entries.first().key)
            }
            ActiveTurn(runtime.runner, metadata)
        }
    }

    private suspend fun currentTurnForResume(sessionId: String): ActiveTurn =
        runnerMutex.withLock {
            val binding = sessionBindings[sessionId]
                ?: error("No active runner is available for session $sessionId.")
            val runtime = runtimes[binding.key]
                ?: error("No active runner is available for session $sessionId.")
            ActiveTurn(runtime.runner, binding.customMetadata)
        }

    companion object {
        const val APP_NAME: String = AgentSessionIdentity.APP_NAME

        /** 共享运行时（Agent + Runner）的 LRU 上限，按 [AgentKey] 计数。 */
        const val MAX_CACHED_RUNTIMES: Int = 10

        /** 会话绑定的 LRU 上限；绑定很轻量，保留比运行时更长的历史以支持确认恢复。 */
        const val MAX_SESSION_BINDINGS: Int = 50
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
