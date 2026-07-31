package github.ponyhuang.gimi.agent

import com.google.adk.kt.logging.LoggerFactory
import github.ponyhuang.gimi.agent.tools.mcp.McpSessionManager
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * [McpConnectionTester] 的生产实现：对给定配置做一次真实的连接握手 + 能力列举。
 *
 * 使用 [McpSessionManager.createSession] 构建一次性会话（不入池），探测完成后立即关闭，
 * 因此不会污染 [McpToolsetRegistry] 的会话池，失败探测也不会留下缓存。
 */
@Singleton
class AdkMcpConnectionTester @Inject constructor() : McpConnectionTester {

    override suspend fun test(server: McpServer): McpProbeResult {
        val session = McpSessionManager.createSession(server.toMcpConnectionParameters())
        try {
            return withTimeout(PROBE_TIMEOUT) {
                session.connect()
                val client = session.client
                val capabilities = client.serverCapabilities
                McpProbeResult(
                    reachable = true,
                    serverName = client.serverVersion?.name,
                    serverVersion = client.serverVersion?.version,
                    tools = client.listTools().tools.map {
                        McpToolSummary(name = it.name, description = it.description ?: "")
                    },
                    // 服务器声明支持才列举；单个能力调用失败不影响整体探测结论。
                    resources = if (capabilities?.resources != null) {
                        runCatching { client.listResources().resources.map { it.name } }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    },
                    prompts = if (capabilities?.prompts != null) {
                        runCatching { client.listPrompts().prompts.map { it.name } }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    },
                )
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "MCP probe timed out for ${server.endpointUrl}" }
            return unreachable("连接超时，请检查服务器地址与网络")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "MCP probe failed for ${server.endpointUrl}: ${e.message}" }
            return unreachable(e.message)
        } finally {
            session.close()
        }
    }

    private fun unreachable(detail: String?) = McpProbeResult(
        reachable = false,
        errorMessage = detail?.takeIf { it.isNotBlank() }?.let { "无法连接到服务器：$it" }
            ?: "无法连接到服务器",
    )

    private companion object {
        val PROBE_TIMEOUT = 10.seconds
        val logger = LoggerFactory.getLogger(AdkMcpConnectionTester::class)
    }
}
