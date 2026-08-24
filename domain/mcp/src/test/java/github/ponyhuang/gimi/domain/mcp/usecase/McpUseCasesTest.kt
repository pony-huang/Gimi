package github.ponyhuang.gimi.domain.mcp.usecase

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
        val result = McpImportResult(created = 2, skipped = 1)
        repository.importResult = result

        assertEquals(result, ManageMcpServersUseCase(repository).importJson("{}"))
        assertEquals(listOf("{}"), repository.importedJson)
    }

    @Test
    fun importForConversationEnablesReadyServersAndTracksCredentialTemplate() = runTest {
        repository.importResult = McpImportResult(
            created = 1,
            updated = 1,
            affectedServerIds = setOf("mcp-d", "mcp-e"),
            credentialRequiredServerIds = setOf("mcp-e"),
        )
        val conversations = FakeConversationRepository(
            storedConfiguration = ConversationToolConfiguration(
                enabledLocalToolIds = setOf("clock"),
                enabledMcpServerIds = setOf("mcp-a", "mcp-b", "mcp-c"),
            ),
        )

        val result = ImportMcpServersForConversationUseCase(repository, conversations)(
            sessionId = "session-1",
            content = "{}",
        )

        assertEquals(true, result.conversationActivated)
        assertEquals(setOf("mcp-d", "mcp-e"), result.importResult.affectedServerIds)
        assertEquals(
            ConversationToolConfiguration(
                enabledLocalToolIds = setOf("clock"),
                enabledMcpServerIds = setOf("mcp-a", "mcp-b", "mcp-c", "mcp-d"),
                pendingMcpCredentialServerId = "mcp-e",
            ),
            conversations.savedConfiguration,
        )
    }

    @Test
    fun updateAuthorizationUsesPendingServerAndClearsConversationMarker() = runTest {
        repository.serverResult = McpServer(id = "mcp-e", name = "zhihu-global-search")
        repository.updateAuthorizationResult = true
        val conversations = FakeConversationRepository(
            storedConfiguration = ConversationToolConfiguration(
                enabledMcpServerIds = setOf("mcp-a"),
                pendingMcpCredentialServerId = "mcp-e",
            ),
        )

        val result = UpdateMcpAuthorizationForConversationUseCase(repository, conversations)(
            sessionId = "session-1",
            authorization = "actual-secret",
        )

        assertEquals(true, result.updated)
        assertEquals("zhihu-global-search", result.serverName)
        assertEquals(listOf("mcp-e" to "Bearer actual-secret"), repository.authorizationUpdates)
        assertEquals(null, conversations.savedConfiguration?.pendingMcpCredentialServerId)
        assertEquals(setOf("mcp-a", "mcp-e"), conversations.savedConfiguration?.enabledMcpServerIds)
    }

    @Test
    fun updateAuthorizationRejectsBearerSchemeWithoutCredential() = runTest {
        repository.serverResult = McpServer(id = "mcp-e", name = "zhihu-global-search")
        repository.updateAuthorizationResult = true
        val conversations = FakeConversationRepository(
            storedConfiguration = ConversationToolConfiguration(
                pendingMcpCredentialServerId = "mcp-e",
            ),
        )

        val result = UpdateMcpAuthorizationForConversationUseCase(repository, conversations)(
            sessionId = "session-1",
            authorization = "Authorization: Bearer",
        )

        assertEquals(false, result.updated)
        assertEquals(emptyList<Pair<String, String>>(), repository.authorizationUpdates)
    }

    @Test
    fun updateAuthorizationAcceptsCompleteAuthorizationHeader() = runTest {
        repository.serverResult = McpServer(id = "mcp-e", name = "zhihu-global-search")
        repository.updateAuthorizationResult = true
        val conversations = FakeConversationRepository(
            storedConfiguration = ConversationToolConfiguration(
                pendingMcpCredentialServerId = "mcp-e",
            ),
        )

        val result = UpdateMcpAuthorizationForConversationUseCase(repository, conversations)(
            sessionId = "session-1",
            authorization = "Authorization: Bearer actual-secret",
        )

        assertEquals(true, result.updated)
        assertEquals(listOf("mcp-e" to "Bearer actual-secret"), repository.authorizationUpdates)
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
        val authorizationUpdates = mutableListOf<Pair<String, String>>()
        var updateAuthorizationResult = false

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

        override fun updateAuthorization(serverId: String, authorization: String): Boolean {
            authorizationUpdates += serverId to authorization
            return updateAuthorizationResult
        }
    }

    private class FakeConversationRepository(
        private val storedConfiguration: ConversationToolConfiguration?,
    ) : ConversationRepository {
        var savedConfiguration: ConversationToolConfiguration? = null

        override val conversations: StateFlow<List<Conversation>> = MutableStateFlow(emptyList())
        override val conversationContentUpdates: SharedFlow<String> = MutableSharedFlow()

        override suspend fun refresh() = Unit
        override suspend fun refreshConversation(sessionId: String) = Unit
        override suspend fun listConversations(): List<Conversation> = emptyList()
        override suspend fun loadMessages(sessionId: String): List<Message>? = emptyList()
        override suspend fun lastConversationId(): String? = null
        override suspend fun activateConversation(sessionId: String, defaultModel: String): String = defaultModel
        override suspend fun setConversationModel(sessionId: String, model: String) = Unit
        override suspend fun conversationToolConfiguration(
            sessionId: String,
        ): ConversationToolConfiguration? = storedConfiguration

        override suspend fun setConversationToolConfiguration(
            sessionId: String,
            configuration: ConversationToolConfiguration,
        ): Boolean {
            savedConfiguration = configuration
            return true
        }

        override suspend fun discardConversationMetadata(sessionId: String) = Unit
        override suspend fun createConversation(
            initialModel: String,
            activate: Boolean,
            initialToolConfiguration: ConversationToolConfiguration?,
        ): String = "session"

        override suspend fun deleteConversation(sessionId: String) = Unit
        override fun notifyConversationContentChanged(sessionId: String) = Unit
    }
}
