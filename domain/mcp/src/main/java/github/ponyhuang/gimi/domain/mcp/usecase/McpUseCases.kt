package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.mcp.model.McpServer
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
