package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.mcp.model.McpConversationImportResult
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
 * 从 Agent 导入 MCP 配置，并把成功新增或更新的服务器加入当前会话。
 */
class ImportMcpServersForConversationUseCase @Inject constructor(
    private val mcpRepository: McpRepository,
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        json: String,
    ): McpConversationImportResult {
        val importResult = mcpRepository.importJson(json)
        if (importResult.error != null || importResult.affectedServerIds.isEmpty()) {
            return McpConversationImportResult(importResult, conversationActivated = false)
        }
        val current = conversationRepository.conversationToolConfiguration(sessionId)
            ?: return McpConversationImportResult(importResult, conversationActivated = false)
        val updated = current.copy(
            enabledMcpServerIds = current.enabledMcpServerIds + importResult.affectedServerIds,
        )
        val activated = updated == current || conversationRepository.setConversationToolConfiguration(
            sessionId,
            updated,
        )
        return McpConversationImportResult(importResult, conversationActivated = activated)
    }
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
