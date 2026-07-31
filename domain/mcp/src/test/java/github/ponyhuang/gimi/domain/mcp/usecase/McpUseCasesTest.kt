package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class McpUseCasesTest {

    private val repository = FakeMcpRepository()

    @Test
    fun observeServersExposesRepositoryFlow() = runTest {
        val servers = listOf(McpServer(name = "search"), McpServer(name = "files"))
        repository.servers.value = servers

        assertEquals(servers, ObserveMcpServersUseCase(repository)().first())
    }

    @Test
    fun manageServersLookUpDelegatesToRepository() {
        val server = McpServer(name = "search")
        repository.serverResult = server

        assertSame(server, ManageMcpServersUseCase(repository).server("id-1"))
        assertEquals(listOf("id-1"), repository.serverCalls)
    }

    @Test
    fun manageServersSaveDelegatesToRepository() {
        val server = McpServer(name = "search")

        ManageMcpServersUseCase(repository).save(server)

        assertEquals(listOf(server), repository.savedServers)
    }

    @Test
    fun manageServersDeleteDelegatesToRepository() {
        ManageMcpServersUseCase(repository).delete("id-9")

        assertEquals(listOf("id-9"), repository.deletedIds)
    }

    @Test
    fun manageServersImportJsonDelegatesAndReturnsResult() {
        val result = McpImportResult(imported = 2, skipped = 1)
        repository.importResult = result

        assertEquals(result, ManageMcpServersUseCase(repository).importJson("{}"))
        assertEquals(listOf("{}"), repository.importedJson)
    }

    @Test
    fun testConnectionDelegatesToTester() = runTest {
        val server = McpServer(name = "search")
        val tester = FakeMcpConnectionTester()
        tester.result = McpProbeResult(reachable = true)

        assertEquals(
            McpProbeResult(reachable = true),
            TestMcpConnectionUseCase(tester)(server),
        )
        assertEquals(listOf(server), tester.testedServers)
    }

    @Test
    fun fetchCapabilitiesResolvesServerThenDelegatesToTester() = runTest {
        val server = McpServer(name = "search")
        repository.serverResult = server
        val tester = FakeMcpConnectionTester()
        tester.result = McpProbeResult(reachable = true)

        assertEquals(
            McpProbeResult(reachable = true),
            FetchMcpServerCapabilitiesUseCase(tester, repository)("id-1"),
        )
        assertEquals(listOf("id-1"), repository.serverCalls)
        assertEquals(listOf(server), tester.testedServers)
    }

    @Test
    fun fetchCapabilitiesReturnsNullWhenServerIsMissing() = runTest {
        repository.serverResult = null
        val tester = FakeMcpConnectionTester()

        assertEquals(null, FetchMcpServerCapabilitiesUseCase(tester, repository)("id-404"))
        assertEquals(emptyList<McpServer>(), tester.testedServers)
    }

    private class FakeMcpConnectionTester : McpConnectionTester {
        var result = McpProbeResult(reachable = false)
        val testedServers = mutableListOf<McpServer>()

        override suspend fun test(server: McpServer): McpProbeResult {
            testedServers += server
            return result
        }
    }

    private class FakeMcpRepository : McpRepository {
        val servers = MutableStateFlow<List<McpServer>>(emptyList())
        var serverResult: McpServer? = null
        var importResult = McpImportResult()
        val serverCalls = mutableListOf<String>()
        val savedServers = mutableListOf<McpServer>()
        val deletedIds = mutableListOf<String>()
        val importedJson = mutableListOf<String>()

        override val revision: StateFlow<Long> = MutableStateFlow(0L)

        override fun observeServers(): Flow<List<McpServer>> = servers

        override fun currentServers(): List<McpServer> = servers.value

        override fun server(id: String): McpServer? {
            serverCalls += id
            return serverResult
        }

        override fun save(server: McpServer) {
            savedServers += server
        }

        override fun delete(id: String) {
            deletedIds += id
        }

        override fun importJson(json: String): McpImportResult {
            importedJson += json
            return importResult
        }
    }
}
