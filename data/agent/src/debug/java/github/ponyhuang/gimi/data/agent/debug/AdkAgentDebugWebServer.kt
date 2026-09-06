package github.ponyhuang.gimi.data.agent.debug

import android.util.Log
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.webserver.AdkWebServer
import com.google.adk.kt.webserver.loaders.AgentLoader
import com.google.adk.kt.webserver.telemetry.ApiServerSpanExporter
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentFactory
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import com.google.adk.kt.sessions.SessionService
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AgentDebugWebServer] 的 debug 实现 — 在手机上启动 ADK `AdkWebServer`（Ktor/Netty），
 * 供 PC 浏览器经局域网访问 Development UI（`/dev-ui`）。
 *
 * 设计要点：
 * - Agent 与聊天链路同源构建（[AgentFactory] + 默认模型 + 常驻工具集），并通过共享的
 *   [SessionService]（Room）让 Dev UI 直接观察 App 内的真实会话。
 * - Agent 装配较重（模型配置就绪、工具集装配），由 [LazySingleAgentLoader] 推迟到
 *   Dev UI 首次请求时执行，debug 启动不因此变慢。
 * - Dev UI 的 trace 视图需要 `captureMessageContent = true` 才能展示 prompt/response
 *   内容；仅在 debug 构建启用，存在 PII 记录风险，正式包不含此代码。
 * - 插件列表传空：标题生成/记忆持久化等业务插件只服务 App 内聊天链路，Dev UI 观察不需要。
 *
 * 线程模型：[stateRef] 用 `null / STARTING_MARKER / AdkWebServer` 三态原子引用防止
 * 并发重复启动；[stop] 可从任意线程调用。
 */
@Singleton
class AdkAgentDebugWebServer @Inject constructor(
    private val agentFactory: AgentFactory,
    private val modelServices: AgentModelConfigurationSource,
    private val pluginRuntimeProvider: PluginRuntimeProvider<AgentPlugin>,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService,
) : AgentDebugWebServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** null = 未启动；[STARTING_MARKER] = 启动中；[AdkWebServer] = 运行中。 */
    private val stateRef = AtomicReference<Any?>(null)

    override fun start() {
        if (!stateRef.compareAndSet(null, STARTING_MARKER)) return
        scope.launch {
            try {
                val webServer = AdkWebServer(
                    port = AgentDebugWebServer.DEFAULT_PORT,
                    sessionService = sessionService,
                    artifactService = artifactService,
                    agentLoader = LazySingleAgentLoader(::buildAgent),
                    apiServerSpanExporter = ApiServerSpanExporter(),
                    captureMessageContent = true,
                    plugins = emptyList(),
                )
                webServer.start()
                stateRef.set(webServer)
                logLanAddresses()
            } catch (e: CancellationException) {
                stateRef.set(null)
                throw e
            } catch (e: Exception) {
                // debug 工具失败不影响宿主功能，记录后复位状态，允许下次 start 重试。
                stateRef.set(null)
                Log.e(TAG, "ADK debug webserver failed to start", e)
            }
        }
    }

    override fun stop() {
        (stateRef.getAndSet(null) as? AdkWebServer)?.let { server ->
            runCatching { server.stop() }
                .onFailure { Log.w(TAG, "ADK debug webserver stop failed", it) }
            Log.i(TAG, "ADK debug webserver stopped")
        }
    }

    /** 按聊天链路同源规则构建 Agent（默认模型 + 常驻工具集）。 */
    private suspend fun buildAgent(): BaseAgent {
        modelServices.awaitReady()
        val runtime = agentFactory.create(
            AgentBuildSpec(pluginRuntime = pluginRuntimeProvider.runtime.value),
        )
        return runtime.agent
    }

    /** 输出本机局域网地址，方便在 PC 浏览器直接拼 Dev UI 地址。 */
    private fun logLanAddresses() {
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .forEach { address ->
                    Log.i(
                        TAG,
                        "ADK Dev UI: http://${address.hostAddress}:${AgentDebugWebServer.DEFAULT_PORT}/dev-ui",
                    )
                }
        }
    }

    /**
     * 首次被 Dev UI 请求时才构建 Agent 的 [AgentLoader]。
     *
     * ADK `AgentLoader` 是阻塞签名而 Agent 构建需要挂起（模型配置就绪等待），
     * 这里用 [runBlocking] 在 Ktor 工作线程上桥接；仅首次触发昂贵构建，
     * 结果缓存后复用。
     *
     * @property buildAgent 挂起构建 Agent 的闭包。
     */
    private class LazySingleAgentLoader(
        private val buildAgent: suspend () -> BaseAgent,
    ) : AgentLoader {
        private val mutex = Mutex()

        @Volatile
        private var cached: BaseAgent? = null

        private suspend fun agent(): BaseAgent =
            cached ?: mutex.withLock { cached ?: buildAgent().also { cached = it } }

        override fun listAgents(): List<String> = listOf(runBlocking { agent() }.name)

        override fun loadAgent(agentName: String): BaseAgent = runBlocking {
            try {
                agent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ADK debug webserver failed to build agent", e)
                throw e
            }
        }
    }

    companion object {
        private const val TAG: String = "AdkAgentDebugWebServer"

        /** [stateRef] 的「启动中」占位标记。 */
        private val STARTING_MARKER = Any()
    }
}
