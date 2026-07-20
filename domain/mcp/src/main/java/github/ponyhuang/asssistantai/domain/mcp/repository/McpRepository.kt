package github.ponyhuang.asssistantai.domain.mcp.repository

import github.ponyhuang.asssistantai.domain.mcp.model.McpImportResult
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import kotlinx.coroutines.flow.Flow

interface McpRepository {
    fun observeServers(): Flow<List<McpServer>>

    fun currentServers(): List<McpServer>

    fun server(id: String): McpServer?

    fun save(server: McpServer)

    fun delete(id: String)

    fun importJson(json: String): McpImportResult
}
