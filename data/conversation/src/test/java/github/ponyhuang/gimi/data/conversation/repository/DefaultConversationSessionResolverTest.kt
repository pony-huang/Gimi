package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultConversationSessionResolverTest {
    private val selection = ModelSelection("service", "chat", "model")
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val modelCatalog = mockk<ModelCatalogRepository> {
        coEvery { awaitReady() } returns Unit
        every { currentAssistantSelection() } returns selection
        every { currentServices() } returns listOf(service())
    }
    private val mcpRepository = mockk<McpRepository>(relaxed = true) {
        every { currentServers() } returns listOf(
            McpServer(id = "mcp", name = "MCP", isEnabled = true),
        )
    }
    private val toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true) {
        every { tools } returns MutableStateFlow(
            listOf(ToolDescriptor("clock", "Clock", "Clock", true)),
        )
        every { enabledToolIds() } returns setOf("clock")
    }
    private val resolver = DefaultConversationSessionResolver(
        conversations,
        modelCatalog,
        mcpRepository,
        toolAuthorization,
    )

    @Test
    fun resolveCurrentOrCreateUsesCurrentConversationModelAndTools() = runTest {
        val tools = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock"),
            enabledMcpServerIds = setOf("mcp"),
            enabledOfficialFunctionIdsByService = mapOf(
                "service" to mapOf(
                    "web_search" to setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
                ),
            ),
        )
        coEvery { conversations.lastConversationId() } returns "current"
        coEvery { conversations.loadMessages("current") } returns emptyList()
        coEvery { conversations.activateConversation("current", any()) } returns
            ModelSelectionCodec.encode(selection)
        coEvery { conversations.conversationToolConfiguration("current") } returns tools

        val result = resolver.resolveCurrentOrCreate()

        assertEquals("current", result.sessionId)
        assertEquals(selection, result.modelSelection)
        assertEquals(tools, result.toolConfiguration)
        coVerify(exactly = 0) { conversations.createConversation(any(), any(), any()) }
        coVerify(exactly = 0) { conversations.setConversationModel(any(), any()) }
    }

    @Test
    fun resolveCurrentOrCreateDropsMissingCurrentAndRestoresMostRecent() = runTest {
        coEvery { conversations.lastConversationId() } returns "missing"
        coEvery { conversations.loadMessages("missing") } returns null
        coEvery { conversations.listConversations() } returns listOf(
            Conversation(id = "recent", title = "Recent"),
        )
        coEvery { conversations.loadMessages("recent") } returns emptyList()
        coEvery { conversations.activateConversation("recent", any()) } returns
            ModelSelectionCodec.encode(selection)
        coEvery { conversations.conversationToolConfiguration("recent") } returns
            ConversationToolConfiguration()
        coEvery { conversations.setConversationToolConfiguration(any(), any()) } returns true

        val result = resolver.resolveCurrentOrCreate()

        assertEquals("recent", result.sessionId)
        coVerify { conversations.discardConversationMetadata("missing") }
    }

    @Test
    fun createAndActivateSnapshotsOrdinaryConversationDefaults() = runTest {
        val configuration = slot<ConversationToolConfiguration>()
        coEvery {
            conversations.createConversation(any(), true, capture(configuration))
        } returns "new"

        val result = resolver.createAndActivate()

        assertEquals("new", result.sessionId)
        assertEquals(setOf("clock"), configuration.captured.enabledLocalToolIds)
        assertEquals(setOf("mcp"), configuration.captured.enabledMcpServerIds)
        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            configuration.captured.enabledOfficialFunctionIds("service", "web_search"),
        )
    }

    @Test
    fun resolveToolConfigurationDoesNotActivateConversation() = runTest {
        coEvery { conversations.conversationToolConfiguration("background") } returns
            ConversationToolConfiguration(enabledLocalToolIds = setOf("clock"))
        coEvery { conversations.setConversationToolConfiguration(any(), any()) } returns true

        resolver.resolveToolConfiguration("background", selection)

        coVerify(exactly = 0) { conversations.activateConversation(any(), any()) }
    }

    private fun service() = LLMModelSetting(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.test",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "chat",
                name = "Chat",
                models = listOf(Model(id = "model", name = "Model")),
            ),
        ),
        supportedOfficialTools = listOf("web_search"),
    )
}
