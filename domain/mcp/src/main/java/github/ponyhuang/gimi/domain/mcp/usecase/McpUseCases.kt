package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import javax.inject.Inject

class ObserveMcpServersUseCase @Inject constructor(
    private val repository: McpRepository,
) {
    operator fun invoke() = repository.observeServers()
}

class ManageMcpServersUseCase @Inject constructor(
    private val repository: McpRepository,
) {
    fun server(id: String) = repository.server(id)

    fun save(server: McpServer) = repository.save(server)

    fun delete(id: String) = repository.delete(id)

    fun importJson(json: String) = repository.importJson(json)
}

/**
 * 编辑器保存前的实时连通性验证：始终实时探测，不使用缓存。
 */
class TestMcpConnectionUseCase @Inject constructor(
    private val tester: McpConnectionTester,
) {
    suspend operator fun invoke(server: McpServer): McpProbeResult = tester.test(server)
}

/**
 * 列表展开查看服务器能力：按 id 取当前配置后探测。
 */
class FetchMcpServerCapabilitiesUseCase @Inject constructor(
    private val tester: McpConnectionTester,
    private val repository: McpRepository,
) {
    suspend operator fun invoke(serverId: String): McpProbeResult? =
        repository.server(serverId)?.let { tester.test(it) }
}
