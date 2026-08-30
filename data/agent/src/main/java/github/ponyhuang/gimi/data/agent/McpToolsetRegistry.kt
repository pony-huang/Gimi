package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.data.agent.tools.mcp.McpToolset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 将持久化的 [McpServer] 配置转换为 [McpToolset] 实例，供 [AgentFactory] 注入 agent。
 *
 * 替代了旧的 [McpToolRegistry]，采用 [McpToolset] 的会话池化、自动恢复和重试机制，
 * 代替原来每次发现和执行都新建连接的方式。
 *
 * 工具集按 `(revision, selectedServerIds)` 做小容量 LRU 缓存；revision 变化时
 * 全部关闭重建。不同会话的服务器选择交替出现时各自命中缓存，不会反复关闭重连。
 */
@Singleton
class McpToolsetRegistry @Inject constructor(
    private val servers: McpRepository,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<CacheKey, List<McpToolsetHandle>>(8, 0.75f, true)
    private var cachedRevision: Long? = null

    /**
     * 返回当前启用的 MCP 服务器对应的 [McpToolset] 列表及工具名称集合。
     *
     * @param selectedServerIds 会话级别的服务器筛选；为 null 时使用全局 [McpServer.isEnabled]。
     */
    suspend fun resolve(
        selectedServerIds: Set<String>? = null,
    ): McpToolsetResolution = mutex.withLock {
        val revision = servers.revision.value
        if (cachedRevision != revision) {
            closeAll()
            cachedRevision = revision
        }
        val key = CacheKey(revision, selectedServerIds)
        cache[key]?.let { return@withLock McpToolsetResolution(it) }

        val selected = selectMcpServers(servers.currentServers(), selectedServerIds)
        val handles = selected
            .filter { it.endpointUrl.isNotBlank() }
            .mapNotNull { server ->
                cancellationAwareRunCatching {
                    McpToolsetHandle(
                        serverId = server.id,
                        displayName = server.name.ifBlank { server.id },
                        isGloballyEnabled = server.isEnabled,
                        toolset = server.toMcpToolset(),
                    )
                }.getOrNull()
            }

        cache[key] = handles
        // 超出容量时淘汰最久未用的条目并关闭其连接，避免传输资源泄漏。
        while (cache.size > MAX_CACHED_SELECTIONS) {
            val eldest = cache.entries.first()
            eldest.value.forEach { handle -> runCatching { handle.toolset.close() } }
            cache.remove(eldest.key)
        }
        McpToolsetResolution(handles)
    }

    /**
     * 解析全部已配置且地址有效的 MCP server，不在此阶段应用启用开关。
     *
     * `tool_search` 需要先把完整工具目录写入向量索引，再对向量命中结果应用
     * 当前会话选择；因此发现目录时不能只连接已启用 server。
     */
    suspend fun resolveAll(): McpToolsetResolution {
        val allServerIds = servers.currentServers().mapTo(linkedSetOf(), McpServer::id)
        return resolve(allServerIds)
    }

    /** 关闭并清空全部缓存的工具集（释放会话和传输资源）。 */
    private fun closeAll() {
        cache.values.flatten().forEach { handle ->
            runCatching { handle.toolset.close() }
        }
        cache.clear()
    }

    private data class CacheKey(
        val revision: Long,
        val selectedServerIds: Set<String>?,
    )

    private companion object {
        const val MAX_CACHED_SELECTIONS: Int = 4
    }
}

/**
 * 当前会话所选 MCP servers 的惰性 Toolset 解析结果。
 *
 * @property handles 每个 server 独立的绑定；单个来源发现失败不会影响其他来源。
 */
data class McpToolsetResolution(
    val handles: List<McpToolsetHandle>,
)

/**
 * 一个已选择 MCP server 与其惰性 Toolset 的绑定。
 *
 * @property serverId server 的稳定 ID，用于动态候选来源隔离。
 * @property displayName 可安全展示给模型的 server 名称。
 * @property isGloballyEnabled 未提供会话级选择时采用的全局开关状态。
 * @property toolset 负责连接、缓存声明和执行调用的 MCP Toolset。
 */
data class McpToolsetHandle(
    val serverId: String,
    val displayName: String,
    val isGloballyEnabled: Boolean,
    val toolset: McpToolset,
)

/**
 * 按 [selectedServerIds] 筛选服务器列表。
 *
 * - 当 `selectedServerIds` 为 null 时，返回所有 `isEnabled` 的服务器（全局默认行为）。
 * - 当 `selectedServerIds` 非 null 时，仅返回 ID 在该集合中的服务器（会话级覆盖）。
 */
internal fun selectMcpServers(
    servers: List<McpServer>,
    selectedServerIds: Set<String>?,
): List<McpServer> = if (selectedServerIds == null) {
    servers.filter(McpServer::isEnabled)
} else {
    servers.filter { it.id in selectedServerIds }
}
