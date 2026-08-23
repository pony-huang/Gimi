package github.ponyhuang.gimi.agent

import com.google.adk.kt.logging.LoggerFactory
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolLoadingException
import github.ponyhuang.gimi.agent.tools.mcp.McpToolset
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * [McpConnectionTester] 的生产实现：对给定配置做一次真实的连接握手 + 能力列举。
 *
 * 通过 [McpServer.toMcpToolset] 构建一次性 [McpToolset]，用 [McpToolset.getTools] 完成握手并
 * 拉取工具列表，探测完成后立即 [McpToolset.close]，因此不会污染 [McpToolsetRegistry] 的会话池，
 * 失败探测也不会留下缓存。
 */
@Singleton
class AdkMcpConnectionTester @Inject constructor() : McpConnectionTester {

    override suspend fun test(server: McpServer): McpProbeResult {
        val toolset = server.toMcpToolset()
        try {
            return withTimeout(PROBE_TIMEOUT) {
                // getTools 内部完成会话建立 + 握手 + 工具列举，失败会重试后抛出。
                val tools = toolset.getTools(null)
                McpProbeResult(
                    reachable = true,
                    tools = tools.map { McpToolSummary(name = it.name, description = it.description) },
                    resources = runCatching { toolset.listResourceNames() }.getOrDefault(emptyList()),
                )
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "MCP probe timed out for ${server.endpointUrl}" }
            return unreachable("连接超时，请检查服务器地址与网络")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "MCP probe failed for ${server.endpointUrl}: ${e.message}" }
            val detail = (e as? McpToolLoadingException)?.cause?.message ?: e.message
            return unreachable(detail)
        } finally {
            toolset.close()
        }
    }

    private fun unreachable(detail: String?) = McpProbeResult(
        reachable = false,
        errorMessage = detail?.takeIf { it.isNotBlank() }?.let { "无法连接到服务器：$it" }
            ?: "无法连接到服务器",
    )

    private companion object {
        val PROBE_TIMEOUT = 60.seconds
        val logger = LoggerFactory.getLogger(AdkMcpConnectionTester::class)
    }
}
