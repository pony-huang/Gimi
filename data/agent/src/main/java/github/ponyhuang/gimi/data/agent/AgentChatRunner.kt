package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.apps.App
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.plugins.LoggingPlugin
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.summarizer.EventsCompactionConfig
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.data.agent.di.AgentModule
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 共享 Agent/Runner 的构建与缓存入口，为每轮聊天创建独立的执行句柄。
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
 * - 每轮返回显式 [AgentChatExecution]，直接持有 runner 和配置；缓存淘汰不影响等待中的恢复。
 * - 构造期由 [AgentModule] 通过 Hilt 注入 [sessionService]、[artifactService]、[plugins]
 *   及 [configuration]；不持有 in-memory 默认实现。
 * - [factory] 仅在缓存未命中时按需调用，保证模型/访问模式切换立即生效。当前正在
 *   `runAsync` 中的会话不受影响（[createExecution] 入口处已快照 runner 引用）。
 * - 单轮 SDK 协议交给 [AgentChatExecution]；事件到领域模型的转换由
 *   `AdkChatAgentRepository` 负责，不在缓存层处理。
 *
 * 线程模型：缓存查询与构建由 [runnerMutex] 保护；执行句柄独立持有 Runner 引用。
 */
class AgentChatRunner(
    private val factory: suspend (AgentBuildSpec) -> AgentRuntime,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService?,
    private val memoryService: MemoryService?,
    private val configuration: () -> AgentBuildConfigurationSnapshot = {
        AgentBuildConfigurationSnapshot(
            revision = Unit,
            pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()),
        )
    },
    private val plugins: (PluginRuntimeSnapshot<AgentPlugin>) -> List<Plugin> = { emptyList() },
    private val toolAccessRepository: ToolAccessRepository,
    private val mobileUseRepository: MobileUseRepository? = null,
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
    ): AgentChatExecution {
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
                mobileUseOwner = UUID.randomUUID().toString(),
            )
            AgentChatExecution(userId, sessionId, runtime.runner, metadata, mobileUseRepository)
        }
    }

    companion object {
        const val APP_NAME: String = AgentSessionIdentity.APP_NAME

        /** 共享运行时（Agent + Runner）的 LRU 上限，按 [AgentKey] 计数。 */
        const val MAX_CACHED_RUNTIMES: Int = 10
    }
}
