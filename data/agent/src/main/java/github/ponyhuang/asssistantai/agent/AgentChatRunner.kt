package github.ponyhuang.asssistantai.agent

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
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.asssistantai.agent.AgentChatRunner.Companion.MAX_CACHED_RUNNERS
import github.ponyhuang.asssistantai.agent.tools.ToolRunMetadata
import github.ponyhuang.asssistantai.di.AgentModule
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.FileAttachment
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * Agent 聊天运行器 — 把 ADK `InMemoryRunner.runAsync(...)` 封装为 `Flow<Event>`。
 *
 * 设计要点：
 * - 按会话缓存 `InMemoryRunner`（LRU 上限 [MAX_CACHED_RUNNERS]），每个会话持有独立 runner。
 *   仅当模型选择、配置版本、工具访问模式或确认工具开关变化时才重建 —— 会话级
 *   工具勾选通过 `RunConfig.customMetadata`（[ToolRunMetadata]）按请求透传给各
 *   Toolset 自行过滤，不再触发 Agent 重建。
 * - 构造期由 [AgentModule] 通过 Hilt 注入 [sessionService]、[artifactService]、[plugins]
 *   及 [configurationRevision]；不持有 in-memory 默认实现。
 * - [factory] 在 runner 重建时按需调用，保证模型/访问模式切换立即生效。当前正在
 *   `runAsync` 中的会话不受影响（[send] 入口处已快照 runner 引用）。
 * - 不对 `Event` 做任何加工；Event → UI 渲染的合并工作由 `ChatViewModel` 的 reducer 完成。
 * - `runConfig` 默认开启 SSE 流式，便于 UI 端做打字机效果。
 *
 * 线程模型：runner 缓存由 [runnerMutex] 保护，所有调用都通过协程 `Flow` 完成。
 */
class AgentChatRunner(
    private val factory: suspend (
        ModelSelection?,
        ToolAccessMode,
    ) -> BaseAgent,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService?,
    private val configurationRevision: () -> Any = { Unit },
    private val plugins: List<Plugin> = emptyList()
) {
    /**
     * 每个会话持有独立 Runner；仅模型选择、配置版本、访问模式或确认开关变化时重建。
     * [customMetadata] 记录最近一次 [send] 透传的工具配置，供确认恢复时复用。
     */
    private data class RunnerEntry(
        val selection: ModelSelection?,
        val revision: Any,
        val toolAccessMode: ToolAccessMode,
        val allowConfirmationRequiredTools: Boolean,
        val customMetadata: Map<String, Any>,
        val runner: InMemoryRunner,
    )

    private val runners = LinkedHashMap<String, RunnerEntry>(16, 0.75f, true)
    private val runnerMutex = Mutex()

    private fun buildRunner(agent: BaseAgent): InMemoryRunner = InMemoryRunner(
        app = App(
            appName = APP_NAME,
            plugins = mutableListOf(LoggingPlugin()) + plugins,
            rootAgent = agent,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
            // 对话摘要压缩
            eventsCompactionConfig = EventsCompactionConfig()
        ),
        sessionService = sessionService,
        artifactService = artifactService,
    )

    /**
     * 清空所有缓存的 runner。
     *
     * 下次 [send] 时 [factory] 会被重新调用以使用最新的模型/工具配置构建 agent。
     * 当前正在 `runAsync` 中的会话不受影响。
     */
    suspend fun recreate() {
        runnerMutex.withLock {
            runners.clear()
        }
    }

    /**
     * 清空所有缓存的 runner，不创建替代。
     *
     * 当没有可用模型时调用，确保已禁用或删除的模型不会被后续请求使用。
     */
    suspend fun invalidate() {
        runnerMutex.withLock {
            runners.clear()
        }
    }

    /**
     * 释放指定会话的 runner 缓存。
     *
     * @param sessionId 要释放的会话 ID
     */
    suspend fun releaseSession(sessionId: String) {
        runnerMutex.withLock { runners.remove(sessionId) }
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
        val customMetadata = ToolRunMetadata.of(toolConfiguration, allowConfirmationRequiredTools)
        // 快照当前 runner；中途 recreate() 不会改本次 send 的行为。
        val activeRunner = currentRunnerForNewTurn(
            sessionId,
            selection,
            toolConfiguration?.toolAccessMode ?: ToolAccessMode.ALWAYS_AVAILABLE,
            allowConfirmationRequiredTools,
            customMetadata,
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
        }
        val newMessage = Content(
            role = Role.USER,
            parts = parts,
        )
        return activeRunner.runAsync(
            userId = userId,
            sessionId = sessionId,
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
        val entry = currentEntryForResume(sessionId)
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
        return entry.runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = confirmationResponse,
            stateDelta = null,
            // 恢复调用沿用最近一次 send 的工具配置，保证 Toolset 过滤上下文一致。
            runConfig = RunConfig(
                streamingMode = StreamingMode.SSE,
                customMetadata = entry.customMetadata,
            ),
        ).flowOn(Dispatchers.IO)
    }

    private suspend fun currentRunnerForNewTurn(
        sessionId: String,
        selection: ModelSelection?,
        toolAccessMode: ToolAccessMode,
        allowConfirmationRequiredTools: Boolean,
        customMetadata: Map<String, Any>,
    ): InMemoryRunner {
        val expectedRevision = configurationRevision()
        return runnerMutex.withLock {
            runners[sessionId]
                ?.takeIf {
                    it.selection == selection &&
                            it.revision == expectedRevision &&
                            it.toolAccessMode == toolAccessMode &&
                            it.allowConfirmationRequiredTools == allowConfirmationRequiredTools
                }
                ?.copy(customMetadata = customMetadata)
                ?.also { updated -> runners[sessionId] = updated }
                ?.runner
                ?: buildRunner(factory(selection, toolAccessMode)).also { newRunner ->
                    runners[sessionId] = RunnerEntry(
                        selection,
                        expectedRevision,
                        toolAccessMode,
                        allowConfirmationRequiredTools,
                        customMetadata,
                        newRunner,
                    )
                    while (runners.size > MAX_CACHED_RUNNERS) {
                        runners.remove(runners.entries.first().key)
                    }
                }
        }
    }

    private suspend fun currentEntryForResume(sessionId: String): RunnerEntry =
        runnerMutex.withLock {
            runners[sessionId]
                ?: error("No active runner is available for session $sessionId.")
        }

    companion object {
        const val APP_NAME: String = AgentSessionIdentity.APP_NAME
        const val MAX_CACHED_RUNNERS: Int = 10
    }
}
