package github.ponyhuang.gimi.domain.mcp.repository

import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface McpRepository {
    val revision: StateFlow<Long>

    fun observeServers(): Flow<List<McpServer>>

    fun currentServers(): List<McpServer>

    fun server(id: String): McpServer?

    /** 写路径涉及加密与磁盘 I/O，统一挂起以避免调用方阻塞主线程。 */
    suspend fun save(server: McpServer)

    suspend fun delete(id: String)

    suspend fun importConfiguration(content: String): McpImportResult = importJson(content)

    suspend fun importJson(json: String): McpImportResult

    suspend fun updateAuthorization(serverId: String, authorization: String): Boolean
}
