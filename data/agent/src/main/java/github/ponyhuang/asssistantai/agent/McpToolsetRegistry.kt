package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import github.ponyhuang.asssistantai.agent.tools.mcp.McpToolset
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
 * 工具集按 `(revision, selectedServerIds)` 缓存，revision 变化时旧会话自动关闭。
 */
@Singleton
class McpToolsetRegistry @Inject constructor(
    private val servers: McpRepository,
) {
    private val mutex = Mutex()
    private var cachedKey: CacheKey? = null
    private var cachedToolsets: List<McpToolset> = emptyList()
    private var cachedToolNames: Set<String> = emptySet()

    /**
     * 返回当前启用的 MCP 服务器对应的 [McpToolset] 列表及工具名称集合。
     *
     * @param selectedServerIds 会话级别的服务器筛选；为 null 时使用全局 [McpServer.isEnabled]。
     */
    suspend fun resolve(
        selectedServerIds: Set<String>? = null,
    ): McpToolsetResolution = mutex.withLock {
        val key = CacheKey(servers.revision.value, selectedServerIds)
        if (key == cachedKey) {
            return@withLock McpToolsetResolution(cachedToolsets, cachedToolNames)
        }

        // 关闭旧的工具集（释放会话和传输资源）
        cachedToolsets.forEach { toolset ->
            runCatching { toolset.close() }
        }

        val selected = selectMcpServers(servers.currentServers(), selectedServerIds)
        val toolsets = selected
            .filter { it.endpointUrl.isNotBlank() }
            .mapNotNull { server ->
                cancellationAwareRunCatching { server.toMcpToolset() }.getOrNull()
            }

        // 预先获取工具名称用于 instruction prompt。
        // headerProvider 为 null 时 McpToolset 内部会缓存工具列表。
        val names = toolsets.flatMap { toolset ->
            cancellationAwareRunCatching {
                toolset.getTools(null).map(BaseTool::name)
            }.getOrDefault(emptyList())
        }.toSet()

        cachedKey = key
        cachedToolsets = toolsets
        cachedToolNames = names
        McpToolsetResolution(toolsets, names)
    }

    private data class CacheKey(
        val revision: Long,
        val selectedServerIds: Set<String>?,
    )
}

/** [McpToolsetRegistry.resolve] 的返回结果。 */
data class McpToolsetResolution(
    val toolsets: List<McpToolset>,
    val toolNames: Set<String>,
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
