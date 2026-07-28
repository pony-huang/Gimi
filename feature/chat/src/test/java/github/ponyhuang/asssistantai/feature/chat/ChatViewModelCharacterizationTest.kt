package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunPart
import github.ponyhuang.asssistantai.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.MessageRole
import github.ponyhuang.asssistantai.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCharacterizationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun send_preservesOptimisticUserMessage_andCompletesAssistantPartial() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.send("你好")

        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(MessageRole.User, state.messages[0].role)
        assertEquals("你好", state.messages[0].textParts.single().text)
        assertEquals(MessageRole.Assistant, state.messages[1].role)
        assertEquals("回复", state.messages[1].textParts.single().text)
        assertFalse(state.messages[1].partial)
        assertFalse(state.isAgentRunning)
        coVerify { fixture.conversations.refreshConversation("session-1") }
    }

    @Test
    fun send_withoutConfiguredChatModel_emitsActionableNoticeWithoutRunningAgent() = runTest {
        val fixture = fixture(configured = false)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        assertEquals(ChatNotice.ConfigureChatModel, fixture.viewModel.uiState.value.notice)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        coVerify(exactly = 0) { fixture.agent.send(any(), any(), any(), any()) }
    }

    @Test
    fun consumeNotice_clearsOneShotEffect() = runTest {
        val fixture = fixture(configured = false)
        fixture.viewModel.send("你好")

        fixture.viewModel.consumeNotice()

        assertEquals(null, fixture.viewModel.uiState.value.notice)
    }

    @Test
    fun newConversationSnapshotsGlobalToolAndMcpDefaults() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.reset()
        advanceUntilIdle()

        coVerify {
            fixture.conversations.createConversation(
                any(),
                any(),
                match { configuration ->
                    configuration?.enabledLocalToolIds == setOf("compose_message") &&
                        configuration.enabledMcpServerIds == setOf("enabled-mcp") &&
                        configuration.enabledOfficialToolIds("service") == setOf("web_search")
                },
            )
        }
    }

    @Test
    fun changingSessionToolPersistsAndReleasesOnlyCurrentRunner() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.reset()
        advanceUntilIdle()

        fixture.viewModel.setLocalToolEnabled("compose_message", enabled = false)
        advanceUntilIdle()

        coVerify {
            fixture.conversations.setConversationToolConfiguration(
                "session-1",
                match { "compose_message" !in it.enabledLocalToolIds },
            )
        }
        coVerify { fixture.agent.releaseSession("session-1") }
    }

    @Test
    fun confirmationRequestsAreQueuedAndSensitiveArgumentsAreRedacted() = runTest {
        val fixture = fixture(
            configured = true,
            events = listOf(
                confirmationEvent(
                    confirmation("confirm-1", "compose_message", mapOf("phone" to "13800138000")),
                    confirmation("confirm-2", "read_file", mapOf("path" to "/secret/private.txt")),
                ),
            ),
        )

        fixture.viewModel.send("执行工具")
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(listOf("confirm-1", "confirm-2"), state.pendingToolConfirmations.map { it.confirmationCallId })
        assertTrue(state.pendingToolConfirmations.first().arguments.contains("••••"))
        assertFalse(state.pendingToolConfirmations.first().arguments.contains("13800138000"))
        assertTrue(state.isAgentRunning)
    }

    @Test
    fun runningConversationsCanSwitchAndStreamWithoutCrossContamination() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.restoreOrCreateSession()
        advanceUntilIdle()

        fixture.viewModel.send("ask-a")
        runCurrent()
        agent.emit("session-a", event(text = "answer-a", invocationId = "inv-a"))
        runCurrent()

        fixture.viewModel.reset()
        advanceUntilIdle()
        fixture.viewModel.send("ask-b")
        runCurrent()
        agent.emit("session-b", event(text = "answer-b", invocationId = "inv-b"))
        runCurrent()

        val bState = fixture.viewModel.uiState.value
        assertEquals("session-b", bState.sessionId)
        assertTrue(bState.messages.any { it.textParts.any { part -> part.text == "answer-b" } })
        assertFalse(bState.messages.any { it.textParts.any { part -> part.text == "answer-a" } })
        assertTrue(bState.conversationTaskStatuses["session-a"] is ConversationTaskStatus.Running)
        assertTrue(bState.conversationTaskStatuses["session-b"] is ConversationTaskStatus.Running)

        fixture.viewModel.switchSession("session-a")
        advanceUntilIdle()
        val aState = fixture.viewModel.uiState.value
        assertTrue(aState.messages.any { it.textParts.any { part -> part.text == "answer-a" } })
        assertFalse(aState.messages.any { it.textParts.any { part -> part.text == "answer-b" } })
        fixture.viewModel.stopStreaming()
        runCurrent()

        fixture.viewModel.switchSession("session-b")
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isAgentRunning)
        fixture.viewModel.stopStreaming()
        runCurrent()
    }

    @Test
    fun fourthParallelConversationIsRejected() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("s1", "s2", "s3", "s4"),
        )
        fixture.viewModel.restoreOrCreateSession()
        advanceUntilIdle()
        repeat(3) { index ->
            fixture.viewModel.send("task-$index")
            runCurrent()
            fixture.viewModel.reset()
            advanceUntilIdle()
        }

        fixture.viewModel.send("task-4")
        runCurrent()
        assertEquals(
            ChatNotice.ParallelTaskLimitReached,
            fixture.viewModel.uiState.value.notice,
        )
        assertEquals(3, fixture.viewModel.uiState.value.conversationTaskStatuses.size)

        listOf("s1", "s2", "s3").forEach { sessionId ->
            fixture.viewModel.switchSession(sessionId)
            advanceUntilIdle()
            fixture.viewModel.stopStreaming()
            runCurrent()
        }
    }

    @Test
    fun pendingConfirmationStaysWithItsConversation() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.restoreOrCreateSession()
        advanceUntilIdle()
        fixture.viewModel.send("tool-a")
        runCurrent()
        agent.emit(
            "session-a",
            confirmationEvent(confirmation("confirm-a", "compose_message", emptyMap())),
        )
        runCurrent()

        fixture.viewModel.reset()
        advanceUntilIdle()
        fixture.viewModel.send("task-b")
        runCurrent()
        assertEquals(null, fixture.viewModel.uiState.value.pendingToolConfirmation)
        assertTrue(
            fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"]
                is ConversationTaskStatus.WaitingForConfirmation,
        )

        fixture.viewModel.switchSession("session-a")
        advanceUntilIdle()
        assertEquals("confirm-a", fixture.viewModel.uiState.value.pendingToolConfirmation?.confirmationCallId)
        fixture.viewModel.respondToToolConfirmation(false)
        advanceUntilIdle()
        fixture.viewModel.switchSession("session-b")
        advanceUntilIdle()
        fixture.viewModel.stopStreaming()
        runCurrent()
    }

    @Test
    fun backgroundCompletionAndFailureRemainUnreadUntilConversationIsOpened() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.restoreOrCreateSession()
        advanceUntilIdle()
        fixture.viewModel.send("first")
        runCurrent()
        fixture.viewModel.reset()
        advanceUntilIdle()

        agent.emit(
            "session-a",
            event(text = "done", partial = false, turnComplete = true),
        )
        agent.complete("session-a")
        advanceUntilIdle()
        assertEquals(
            ConversationTaskStatus.Completed,
            fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"],
        )

        fixture.viewModel.switchSession("session-a")
        advanceUntilIdle()
        assertEquals(null, fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"])

        fixture.viewModel.send("second")
        runCurrent()
        fixture.viewModel.switchSession("session-b")
        advanceUntilIdle()
        agent.emit(
            "session-a",
            event(text = "", partial = false).copy(errorMessage = "failed"),
        )
        agent.complete("session-a")
        advanceUntilIdle()
        assertEquals(
            ConversationTaskStatus.Failed,
            fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"],
        )
        fixture.viewModel.switchSession("session-a")
        advanceUntilIdle()
        assertEquals(null, fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"])
    }

    @Test
    fun activeConversationCannotBeDeletedAndOnlyItsOwnModelIsLocked() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.restoreOrCreateSession()
        advanceUntilIdle()
        fixture.viewModel.send("running-a")
        runCurrent()
        fixture.viewModel.reset()
        advanceUntilIdle()

        fixture.viewModel.deleteConversation("session-a")
        assertEquals(ChatNotice.ActiveConversationDeleteBlocked, fixture.viewModel.uiState.value.notice)
        coVerify(exactly = 0) { fixture.conversations.deleteConversation("session-a") }

        val otherModel = ModelSelection("service", "chat", "other")
        fixture.viewModel.selectModel(otherModel)
        advanceUntilIdle()
        coVerify { fixture.conversations.setConversationModel("session-b", any()) }

        fixture.viewModel.switchSession("session-a")
        advanceUntilIdle()
        fixture.viewModel.selectModel(otherModel)
        assertEquals(ChatNotice.ModelSwitchBlocked, fixture.viewModel.uiState.value.notice)
        fixture.viewModel.stopStreaming()
        runCurrent()
    }

    private inner class ControllableAgent : ChatAgentRepository {
        private val eventChannels = mutableMapOf<String, Channel<ChatRunEvent>>()

        private fun events(sessionId: String): Channel<ChatRunEvent> =
            eventChannels.getOrPut(sessionId) { Channel(Channel.UNLIMITED) }

        suspend fun emit(sessionId: String, event: ChatRunEvent) {
            events(sessionId).send(event)
        }

        fun complete(sessionId: String) {
            eventChannels.remove(sessionId)?.close()
        }

        override suspend fun send(
            sessionId: String,
            selection: ModelSelection,
            text: String,
            fileAttachments: List<github.ponyhuang.asssistantai.domain.conversation.model.FileAttachment>,
            toolConfiguration: ConversationToolConfiguration?,
        ): Flow<ChatRunEvent> = events(sessionId).receiveAsFlow()

        override suspend fun respondToToolConfirmation(
            sessionId: String,
            confirmationCallId: String,
            confirmed: Boolean,
        ): Flow<ChatRunEvent> = flowOf(
            event(
                text = "confirmation handled",
                invocationId = "confirm-$sessionId",
                partial = false,
                turnComplete = true,
            ),
        )

        override suspend fun releaseSession(sessionId: String) = Unit
    }

    private fun fixture(
        configured: Boolean,
        events: List<ChatRunEvent> = listOf(
            event(partial = true, turnComplete = false),
            event(partial = false, turnComplete = true),
        ),
        agentOverride: ChatAgentRepository? = null,
        sessionIds: List<String> = listOf("session-1"),
    ): Fixture {
        val selection = ModelSelection("service", "chat", "model")
        val services = if (configured) listOf(service()) else emptyList()
        val catalog = mockk<ModelCatalogRepository>(relaxed = true) {
            every { observeServices() } returns MutableStateFlow(services)
            every { observeLoadState() } returns MutableStateFlow(CatalogLoadState.Ready)
            every { currentServices() } returns services
            every { currentAssistantSelection() } returns selection.takeIf { configured }
            coEvery { awaitReady() } returns Unit
        }
        val conversations = mockk<ConversationRepository>(relaxed = true) {
            every { this@mockk.conversations } returns MutableStateFlow(emptyList())
            every { conversationContentUpdates } returns MutableSharedFlow()
            coEvery { createConversation(any(), any(), any()) } returnsMany sessionIds
            coEvery { activateConversation(any(), any()) } returns ModelSelectionCodec.encode(selection)
            coEvery { loadMessages(any()) } returns emptyList()
            coEvery { conversationToolConfiguration(any()) } returns null
            coEvery { setConversationToolConfiguration(any(), any()) } returns true
        }
        val agent = agentOverride ?: mockk<ChatAgentRepository>(relaxed = true) {
            coEvery { send(any(), any(), any(), any(), any()) } returns flowOf(*events.toTypedArray())
        }
        val display = mockk<ChatDisplayRepository> {
            every { showToolActivity } returns MutableStateFlow(true)
        }
        val recognition = mockk<SpeechRecognitionRepository>(relaxed = true) {
            every { availability } returns MutableStateFlow(false)
        }
        val playback = mockk<SpeechPlaybackRepository>(relaxed = true) {
            every { state } returns MutableStateFlow(SpeechPlaybackState())
            every { errors } returns MutableSharedFlow()
        }
        val attachments = mockk<ChatAttachmentRepository> {
            coEvery { read(any(), any()) } returns emptyList()
            coEvery { deleteDrafts(any()) } returns Unit
            coEvery { deleteSession(any()) } returns Unit
        }
        val toolAuthorization = mockk<ToolAuthorizationRepository>(relaxed = true) {
            every { tools } returns MutableStateFlow(
                listOf(
                    ToolDescriptor(
                        id = "compose_message",
                        name = "compose_message",
                        description = "撰写短信",
                        isEnabled = true,
                    ),
                ),
            )
            every { enabledToolIds() } returns setOf("compose_message")
        }
        val mcpRepository = mockk<McpRepository>(relaxed = true) {
            every { observeServers() } returns MutableStateFlow(
                listOf(
                    McpServer(id = "enabled-mcp", name = "Enabled", isEnabled = true),
                    McpServer(id = "disabled-mcp", name = "Disabled", isEnabled = false),
                ),
            )
            every { currentServers() } returns listOf(
                McpServer(id = "enabled-mcp", name = "Enabled", isEnabled = true),
                McpServer(id = "disabled-mcp", name = "Disabled", isEnabled = false),
            )
        }
        return Fixture(
            viewModel = ChatViewModel(
                runner = agent,
                agentRuntimeGate = TestAgentRuntimeGate(),
                repository = conversations,
                modelServices = catalog,
                chatDisplayPreferences = display,
                speechRecognitionRepository = recognition,
                speechPlaybackController = playback,
                attachments = attachments,
                toolAuthorization = toolAuthorization,
                mcpRepository = mcpRepository,
            ),
            conversations = conversations,
            agent = agent,
        )
    }

    private fun event(
        partial: Boolean = true,
        turnComplete: Boolean = false,
        text: String = "回复",
        invocationId: String = "invocation-1",
    ) = ChatRunEvent(
        id = "event-1",
        invocationId = invocationId,
        author = "assistant",
        parts = listOf(ChatRunPart(text = text)),
        functionCalls = emptyList(),
        functionResponses = emptyList(),
        partial = partial,
        turnComplete = turnComplete,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )

    private fun confirmationEvent(vararg calls: ChatFunctionCall) = ChatRunEvent(
        id = "confirmation-event",
        invocationId = "invocation-1",
        author = "assistant",
        parts = emptyList(),
        functionCalls = calls.toList(),
        functionResponses = emptyList(),
        partial = false,
        turnComplete = false,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )

    private fun confirmation(id: String, toolName: String, args: Map<String, Any?>) = ChatFunctionCall(
        id = id,
        name = "adk_request_confirmation",
        args = emptyMap(),
        confirmationRequest = ToolConfirmationRequest(toolName = toolName, args = args),
    )

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

    private data class Fixture(
        val viewModel: ChatViewModel,
        val conversations: ConversationRepository,
        val agent: ChatAgentRepository,
    )
}
