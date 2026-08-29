package github.ponyhuang.gimi.feature.chat

import android.util.Log
import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.model.MemoryRuntimeFailure
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCharacterizationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun send_preservesOptimisticUserMessage_andCompletesAssistantPartial() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.Send("你好"))

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
    fun memoryFailuresEmitLocalizedNoticeTypes() = runTest {
        val failures = MutableSharedFlow<MemoryRuntimeFailure>(extraBufferCapacity = 2)
        val fixture = fixture(configured = true, memoryFailures = failures)

        fixture.viewModel.effects.test {
            runCurrent()
            failures.emit(MemoryRuntimeFailure(MemoryOperation.SEARCH))
            assertEquals(ChatEffect.ShowNotice(ChatNotice.MemorySearchFailed), awaitItem())
            failures.emit(MemoryRuntimeFailure(MemoryOperation.WRITE))
            assertEquals(ChatEffect.ShowNotice(ChatNotice.MemoryWriteFailed), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_withoutConfiguredChatModel_emitsActionableNoticeWithoutRunningAgent() = runTest {
        val fixture = fixture(configured = false)

        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(ChatAction.Send("你好"))
            advanceUntilIdle()

            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.ConfigureChatModel),
                awaitItem(),
            )
            assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
            coVerify(exactly = 0) { fixture.agent.send(any(), any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newConversationSnapshotsGlobalToolAndMcpDefaults() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()

        coVerify {
            fixture.conversations.createConversation(
                any(),
                any(),
                match { configuration ->
                    configuration?.enabledLocalToolIds == setOf("compose_message") &&
                        configuration.enabledMcpServerIds == setOf("enabled-mcp") &&
                        configuration.enabledOfficialFunctionIds(
                            "service",
                            "web_search",
                        ) == setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER)
                },
            )
        }
    }

    @Test
    fun sendReloadsPersistedToolConfigurationAfterAgentImportsMcpServers() = runTest {
        val fixture = fixture(configured = true)
        val beforeImport = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("compose_message"),
            enabledMcpServerIds = setOf("mcp-a", "mcp-b", "mcp-c"),
        )
        val afterImport = beforeImport.copy(
            enabledMcpServerIds = beforeImport.enabledMcpServerIds + setOf("mcp-d", "mcp-e"),
        )
        every { fixture.mcpRepository.currentServers() } returns
            listOf("mcp-a", "mcp-b", "mcp-c", "mcp-d", "mcp-e").map { id ->
                McpServer(id = id, name = id, isEnabled = true)
            }
        coEvery {
            fixture.conversations.conversationToolConfiguration("session-1")
        } returnsMany listOf(beforeImport, afterImport)

        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("use the new tools"))
        advanceUntilIdle()

        coVerify {
            fixture.agent.send(
                "session-1",
                any(),
                "use the new tools",
                any(),
                match {
                    it.enabledLocalToolIds == afterImport.enabledLocalToolIds &&
                        it.enabledMcpServerIds == afterImport.enabledMcpServerIds
                },
            )
        }
        assertEquals(
            afterImport.enabledMcpServerIds,
            fixture.viewModel.uiState.value.toolConfiguration?.enabledMcpServerIds,
        )
    }

    @Test
    fun setDarkThemeDelegatesToDisplayPreferences() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.onAction(ChatAction.SetDarkTheme(true))
        advanceUntilIdle()

        verify { fixture.display.setDarkThemeOverride(true) }
    }

    @Test
    fun changingSessionToolPersistsAndReleasesOnlyCurrentRunner() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()

        fixture.viewModel.onAction(ChatAction.SetLocalToolEnabled("compose_message", enabled = false))
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
    fun changingToolAccessModePersistsAndReleasesOnlyCurrentRunner() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()

        fixture.viewModel.onAction(ChatAction.SetToolAccessMode(ToolAccessMode.ON_DEMAND))
        advanceUntilIdle()

        coVerify {
            fixture.conversations.setConversationToolConfiguration(
                "session-1",
                match { it.toolAccessMode == ToolAccessMode.ON_DEMAND },
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

        fixture.viewModel.onAction(ChatAction.Send("执行工具"))
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
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        fixture.viewModel.onAction(ChatAction.Send("ask-a"))
        runCurrent()
        agent.emit("session-a", event(text = "answer-a", invocationId = "inv-a"))
        runCurrent()

        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("ask-b"))
        runCurrent()
        agent.emit("session-b", event(text = "answer-b", invocationId = "inv-b"))
        runCurrent()

        val bState = fixture.viewModel.uiState.value
        assertEquals("session-b", bState.sessionId)
        assertTrue(bState.messages.any { it.textParts.any { part -> part.text == "answer-b" } })
        assertFalse(bState.messages.any { it.textParts.any { part -> part.text == "answer-a" } })
        assertTrue(bState.conversationTaskStatuses["session-a"] is ConversationTaskStatus.Running)
        assertTrue(bState.conversationTaskStatuses["session-b"] is ConversationTaskStatus.Running)

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        val aState = fixture.viewModel.uiState.value
        assertTrue(aState.messages.any { it.textParts.any { part -> part.text == "answer-a" } })
        assertFalse(aState.messages.any { it.textParts.any { part -> part.text == "answer-b" } })
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        runCurrent()

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isAgentRunning)
        fixture.viewModel.onAction(ChatAction.StopStreaming)
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
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        repeat(3) { index ->
            fixture.viewModel.onAction(ChatAction.Send("task-$index"))
            runCurrent()
            fixture.viewModel.onAction(ChatAction.NewConversation)
            advanceUntilIdle()
        }

        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(ChatAction.Send("task-4"))
            runCurrent()
            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.ParallelTaskLimitReached),
                awaitItem(),
            )
            assertEquals(3, fixture.viewModel.uiState.value.conversationTaskStatuses.size)

            listOf("s1", "s2", "s3").forEach { sessionId ->
                fixture.viewModel.onAction(ChatAction.SwitchSession(sessionId))
                advanceUntilIdle()
                fixture.viewModel.onAction(ChatAction.StopStreaming)
                runCurrent()
            }
            cancelAndIgnoreRemainingEvents()
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
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("tool-a"))
        runCurrent()
        agent.emit(
            "session-a",
            confirmationEvent(confirmation("confirm-a", "compose_message", emptyMap())),
        )
        runCurrent()

        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("task-b"))
        runCurrent()
        assertEquals(null, fixture.viewModel.uiState.value.pendingToolConfirmation)
        assertTrue(
            fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"]
                is ConversationTaskStatus.WaitingForConfirmation,
        )

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        assertEquals("confirm-a", fixture.viewModel.uiState.value.pendingToolConfirmation?.confirmationCallId)
        fixture.viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed = false))
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        runCurrent()
    }

    @Test
    fun fullAccessAutoApprovesConfirmationWithoutShowingCard() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.toolApproval.setFullAccess(true)
        fixture.viewModel.onAction(ChatAction.Send("执行工具"))
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
        )
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        assertEquals(listOf("confirm-1" to true), agent.confirmationResponses)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
    }

    @Test
    fun alwaysAllowedToolAutoApprovesConfirmationWithoutShowingCard() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.toolApproval.setAlwaysAllowed("compose_message")
        fixture.viewModel.onAction(ChatAction.Send("执行工具"))
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
        )
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        assertEquals(listOf("confirm-1" to true), agent.confirmationResponses)
    }

    @Test
    fun alwaysAllowResponsePersistsToolToWhitelist() = runTest {
        val fixture = fixture(
            configured = true,
            events = listOf(
                confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
            ),
        )
        fixture.viewModel.onAction(ChatAction.Send("执行工具"))
        advanceUntilIdle()
        assertEquals(
            "confirm-1",
            fixture.viewModel.uiState.value.pendingToolConfirmation?.confirmationCallId,
        )

        fixture.viewModel.onAction(
            ChatAction.RespondToToolConfirmation(confirmed = true, alwaysAllow = true),
        )
        advanceUntilIdle()

        assertTrue("compose_message" in fixture.toolApproval.alwaysAllowedToolNames.value)
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
    }

    @Test
    fun enablingFullAccessReleasesAlreadyPendingConfirmation() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.viewModel.onAction(ChatAction.Send("执行工具"))
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
        )
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()
        assertEquals(
            "confirm-1",
            fixture.viewModel.uiState.value.pendingToolConfirmation?.confirmationCallId,
        )

        fixture.viewModel.onAction(ChatAction.SetFullAccess(true))
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.fullAccess)
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        assertEquals(listOf("confirm-1" to true), agent.confirmationResponses)
    }

    @Test
    fun backgroundCompletionAndFailureRemainUnreadUntilConversationIsOpened() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("first"))
        runCurrent()
        fixture.viewModel.onAction(ChatAction.NewConversation)
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

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        assertEquals(null, fixture.viewModel.uiState.value.conversationTaskStatuses["session-a"])

        fixture.viewModel.onAction(ChatAction.Send("second"))
        runCurrent()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
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
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
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
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.Send("running-a"))
        runCurrent()
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()

        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(ChatAction.DeleteConversation("session-a"))
            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.ActiveConversationDeleteBlocked),
                awaitItem(),
            )
            coVerify(exactly = 0) { fixture.conversations.deleteConversation("session-a") }

            val otherModel = ModelSelection("service", "chat", "other")
            fixture.viewModel.onAction(ChatAction.SelectModel(otherModel))
            advanceUntilIdle()
            coVerify { fixture.conversations.setConversationModel("session-b", any()) }

            fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
            advanceUntilIdle()
            fixture.viewModel.onAction(ChatAction.SelectModel(otherModel))
            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.ModelSwitchBlocked),
                awaitItem(),
            )
            fixture.viewModel.onAction(ChatAction.StopStreaming)
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancelledOfficialFunctionLoadDoesNotSwallowCancellationException() = runTest {
        val catalog = mockk<OfficialToolFunctionCatalog>(relaxed = true) {
            coEvery { listFunctions("web_search") } throws CancellationException("load cancelled")
        }
        val fixture = fixture(configured = true, officialCatalogOverride = catalog)

        // 会话恢复后 marker 自动展开会触发 web_search 函数列表加载；
        // listFunctions 抛出 CancellationException 时必须向外传播、终止该加载协程，
        // 而不是被吞掉后把 descriptor 写成"加载完成"。
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        val descriptor = fixture.viewModel.uiState.value.officialToolDescriptors
            .first { it.id == "web_search" }
        assertTrue(descriptor.isLoadingFunctions)
        assertEquals(null, descriptor.loadError)
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
            fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
            toolConfiguration: ConversationToolConfiguration?,
        ): Flow<ChatRunEvent> = events(sessionId).receiveAsFlow()

        override suspend fun respondToToolConfirmation(
            sessionId: String,
            confirmationCallId: String,
            confirmed: Boolean,
        ): Flow<ChatRunEvent> {
            confirmationResponses += confirmationCallId to confirmed
            return flowOf(
                event(
                    text = "confirmation handled",
                    invocationId = "confirm-$sessionId",
                    partial = false,
                    turnComplete = true,
                ),
            )
        }

        val confirmationResponses = mutableListOf<Pair<String, Boolean>>()

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
        officialCatalogOverride: OfficialToolFunctionCatalog? = null,
        memoryFailures: MutableSharedFlow<MemoryRuntimeFailure> = MutableSharedFlow(),
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
            every { darkThemeOverride } returns MutableStateFlow(null)
            every { setDarkThemeOverride(any()) } returns Unit
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
        val officialFunctionCatalog = officialCatalogOverride
            ?: mockk<OfficialToolFunctionCatalog>(relaxed = true) {
                coEvery { listFunctions(any()) } returns emptyList()
            }
        val toolApproval = FakeToolApprovalRepository()
        return Fixture(
            viewModel = ChatViewModel(
                runner = agent,
                agentRuntimeGate = FakeAgentRuntimeGate(),
                repository = conversations,
                modelServices = catalog,
                chatDisplayPreferences = display,
                toolApproval = toolApproval,
                speechRecognitionRepository = recognition,
                speechPlaybackController = playback,
                attachments = attachments,
                toolAuthorization = toolAuthorization,
                mcpRepository = mcpRepository,
                mcpSkipReporter = mockk {
                    every { skipped } returns MutableStateFlow(emptyList())
                },
                officialFunctionCatalog = officialFunctionCatalog,
                memoryRuntimeStatus = mockk<MemoryRuntimeStatus>(relaxed = true) {
                    every { failures } returns memoryFailures
                },
            ),
            conversations = conversations,
            agent = agent,
            display = display,
            toolApproval = toolApproval,
            mcpRepository = mcpRepository,
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
        val display: ChatDisplayRepository,
        val toolApproval: FakeToolApprovalRepository,
        val mcpRepository: McpRepository,
    )
}

private class FakeToolApprovalRepository : ToolApprovalRepository {
    private val _alwaysAllowedToolNames = MutableStateFlow<Set<String>>(emptySet())
    private val _fullAccess = MutableStateFlow(false)
    override val alwaysAllowedToolNames = _alwaysAllowedToolNames
    override val fullAccess = _fullAccess

    override fun setAlwaysAllowed(toolName: String) {
        _alwaysAllowedToolNames.value = _alwaysAllowedToolNames.value + toolName
    }

    override fun removeAlwaysAllowed(toolName: String) {
        _alwaysAllowedToolNames.value = _alwaysAllowedToolNames.value - toolName
    }

    override fun setFullAccess(enabled: Boolean) {
        _fullAccess.value = enabled
    }

    override fun isAutoApproved(toolName: String): Boolean =
        _fullAccess.value || toolName in _alwaysAllowedToolNames.value
}
