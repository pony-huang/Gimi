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

    fun save(server: McpServer)

    fun delete(id: String)

    fun importJson(json: String): McpImportResult
}
