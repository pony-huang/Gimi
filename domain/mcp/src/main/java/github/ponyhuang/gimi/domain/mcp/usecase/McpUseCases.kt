package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.mcp.model.McpConversationImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpCredentialUpdateResult
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
 * 从 Agent 导入 MCP 配置；配置完整的服务器立即加入会话，缺少凭据的服务器等待补全后再启用。
 */
class ImportMcpServersForConversationUseCase @Inject constructor(
    private val mcpRepository: McpRepository,
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        content: String,
    ): McpConversationImportResult {
        val importResult = mcpRepository.importConfiguration(content)
        if (importResult.error != null || importResult.affectedServerIds.isEmpty()) {
            return McpConversationImportResult(importResult, conversationActivated = false)
        }
        val current = conversationRepository.conversationToolConfiguration(sessionId)
            ?: return McpConversationImportResult(importResult, conversationActivated = false)
        val pendingCredentialServerId = importResult.credentialRequiredServerIds.lastOrNull()
        val readyServerIds = importResult.affectedServerIds - importResult.credentialRequiredServerIds
        val updated = current.copy(
            // 缺少凭据的 server 先只记录目标，避免补 Token 前触发一次必然失败的握手。
            enabledMcpServerIds = current.enabledMcpServerIds + readyServerIds,
            pendingMcpCredentialServerId = when {
                pendingCredentialServerId != null -> pendingCredentialServerId
                current.pendingMcpCredentialServerId in importResult.affectedServerIds -> null
                else -> current.pendingMcpCredentialServerId
            },
        )
        val activated = updated == current || conversationRepository.setConversationToolConfiguration(
            sessionId,
            updated,
        )
        return McpConversationImportResult(importResult, conversationActivated = activated)
    }
}

/**
 * 把用户随后提供的 Token 或 Authorization 值写入当前会话最近等待凭据的 MCP server。
 */
class UpdateMcpAuthorizationForConversationUseCase @Inject constructor(
    private val mcpRepository: McpRepository,
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        authorization: String,
    ): McpCredentialUpdateResult {
        val normalized = normalizeAuthorization(authorization)
            ?: return McpCredentialUpdateResult(false, error = "Authorization credential is invalid.")
        val configuration = conversationRepository.conversationToolConfiguration(sessionId)
            ?: return McpCredentialUpdateResult(false, error = "The current conversation has no tool configuration.")
        val serverId = configuration.pendingMcpCredentialServerId
            ?: return McpCredentialUpdateResult(false, error = "No MCP server is waiting for credentials.")
        val server = mcpRepository.server(serverId)
            ?: return McpCredentialUpdateResult(false, error = "The pending MCP server no longer exists.")
        if (!mcpRepository.updateAuthorization(serverId, normalized)) {
            return McpCredentialUpdateResult(false, server.name, "Could not update MCP authorization.")
        }
        val markerCleared = conversationRepository.setConversationToolConfiguration(
            sessionId,
            configuration.copy(
                enabledMcpServerIds = configuration.enabledMcpServerIds + serverId,
                pendingMcpCredentialServerId = null,
            ),
        )
        return McpCredentialUpdateResult(
            updated = true,
            serverName = server.name,
            error = if (markerCleared) null else "Authorization was updated, but pending state could not be cleared.",
        )
    }
}

private fun normalizeAuthorization(input: String): String? {
    if ('\r' in input || '\n' in input) return null
    val withoutHeaderName = input.trim().let { value ->
        if (value.startsWith("Authorization:", ignoreCase = true)) {
            value.substringAfter(':').trim()
        } else {
            value
        }
    }
    if (withoutHeaderName.equals("Bearer", ignoreCase = true)) return null
    val credential = if (withoutHeaderName.startsWith("Bearer ", ignoreCase = true)) {
        withoutHeaderName.substringAfter(' ').trim()
    } else {
        withoutHeaderName
    }
    if (
        credential.isBlank() ||
        credential.length > 2_048 ||
        credential.matches(Regex("<[^>]+>")) ||
        credential.matches(Regex("\\$\\{[^}]+}"))
    ) {
        return null
    }
    return "Bearer $credential"
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
