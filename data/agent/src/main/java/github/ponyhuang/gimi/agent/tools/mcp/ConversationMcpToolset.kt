package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.agent.McpToolsetHandle
import github.ponyhuang.gimi.agent.McpToolsetRegistry
import github.ponyhuang.gimi.agent.tools.toolConfigurationOrNull
import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 按会话选择聚合 MCP 工具的 ADK [Toolset]。
 *
 * 每次模型请求时从 invocation 上下文（RunConfig metadata）读取会话勾选的
 * MCP server ID，委托 [McpToolsetRegistry] 惰性解析；metadata 未携带会话配置时
 * 回退到全局启用集合。连接池化由 registry 与各 [McpToolset] 负责。
 *
 * 失败降级：首次 `getTools` 即连通性探测。单个 server 加载失败（含超时）只丢弃该
 * server 的工具并记录到失败缓存，不再把异常抛给 ADK runner（避免会话中出现
 * "Failed to load tools." 错误气泡）；跳过的 server 经 [McpSkipReporter] 发布给 UI。
 * 失败缓存以 registry 缓存的 handles 列表实例为键 —— 配置变更（revision bump）后
 * registry 重建列表，缓存自然失效并重新探测。
 */
@Singleton
class ConversationMcpToolset @Inject constructor(
    private val registry: McpToolsetRegistry,
    private val skipReporter: McpSkipReporter,
) : Toolset {

    private val probeMutex = Mutex()

    /** (registry 缓存的 handles 列表实例 → 已知失败 serverId)，容量有界，LRU 淘汰。 */
    private val probeFailures = mutableListOf<Pair<List<McpToolsetHandle>, MutableSet<String>>>()

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val selectedServerIds = readonlyContext.toolConfigurationOrNull()?.enabledMcpServerIds
        val handles = registry.resolve(selectedServerIds).handles
        if (handles.isEmpty()) return emptyList()

        val knownFailures = probeMutex.withLock { failuresFor(handles).toSet() }
        val newlySkipped = Collections.synchronizedList(mutableListOf<McpSkippedServer>())

        val tools = coroutineScope {
            handles
                .filter { it.serverId !in knownFailures }
                .map { handle ->
                    async {
                        probeCatching { handle.toolset.getTools(readonlyContext) }
                            .onFailure { e ->
                                logger.warn {
                                    "Skipping unreachable MCP server ${handle.displayName}: ${e.message}"
                                }
                                probeMutex.withLock { failuresFor(handles).add(handle.serverId) }
                                newlySkipped.add(
                                    McpSkippedServer(handle.serverId, handle.displayName, e.message),
                                )
                            }
                            .getOrDefault(emptyList())
                    }
                }
                .awaitAll()
                .flatten()
        }

        // 发布当前 resolution 下全部被跳过的 server（含此前已知失败的），
        // 由 UI 按 (sessionId, serverId) 去重提示。
        if (newlySkipped.isNotEmpty()) {
            val allSkipped = handles
                .filter { it.serverId in probeMutex.withLock { failuresFor(handles) } }
                .map { handle ->
                    newlySkipped.firstOrNull { it.serverId == handle.serverId }
                        ?: McpSkippedServer(handle.serverId, handle.displayName)
                }
            skipReporter.publish(allSkipped)
        }
        return tools
    }

    /**
     * 等价于 cancellationAwareRunCatching，但把 [withTimeout] 抛出的
     * [TimeoutCancellationException] 归为可恢复失败（探测超时 ≠ 协程被取消）。
     */
    private suspend inline fun probeCatching(
        crossinline block: suspend () -> List<BaseTool>,
    ): Result<List<BaseTool>> =
        try {
            Result.success(withTimeout(PROBE_BUDGET) { block() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    /** 取该 handles 列表实例对应的失败集合；调用方须持有 [probeMutex]。 */
    private fun failuresFor(handles: List<McpToolsetHandle>): MutableSet<String> {
        val existing = probeFailures.firstOrNull { it.first === handles }
        if (existing != null) {
            // 移到尾部保持 LRU 顺序。
            probeFailures.remove(existing)
            probeFailures.add(existing)
            return existing.second
        }
        val created = handles to mutableSetOf<String>()
        probeFailures.add(created)
        while (probeFailures.size > MAX_TRACKED_SELECTIONS) probeFailures.removeAt(0)
        return created.second
    }

    private companion object {
        /** 单个 server 的探测预算；坏 server 不再放大为 3 次重试 × 5s 串行等待。 */
        val PROBE_BUDGET = 8.seconds

        /** 与 [McpToolsetRegistry] 的 LRU 容量对齐。 */
        const val MAX_TRACKED_SELECTIONS = 4

        val logger = LoggerFactory.getLogger(ConversationMcpToolset::class)
    }
}
