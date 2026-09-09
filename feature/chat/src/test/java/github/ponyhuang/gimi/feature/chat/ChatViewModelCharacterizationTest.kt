package github.ponyhuang.gimi.feature.chat

import android.util.Log
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentExecution
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatSessionRewindException
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatTurnUseCase
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionSnapshot
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
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
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun cancellationWhilePreparingAttachmentsReleasesLeaseAndRunningState() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(
            configured = true,
            agentRuntimeGate = gate,
            attachmentReadFailure = CancellationException("preparation cancelled"),
        )
        fixture.viewModel.send("带附件的请求")
        advanceUntilIdle()
        assertEquals(1, gate.acquisitions.size)
        assertEquals(1, gate.releaseCount)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun failureWhilePreparingAttachmentsReleasesLeaseAndReportsError() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(
            configured = true,
            agentRuntimeGate = gate,
            attachmentReadFailure = java.io.IOException("attachment missing"),
        )
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.send("请求")
        advanceUntilIdle()
        assertEquals(1, gate.releaseCount)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        assertEquals("attachment missing", fixture.viewModel.uiState.value.messages.last().error)
    }

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
    fun failedTurn_retry_reusesOriginalUserMessageWithoutDuplication() = runTest {
        val failingAgent = object : ChatAgentRepository {
            override suspend fun createExecution(
                sessionId: String,
                selection: ModelSelection,
                toolConfiguration: ConversationToolConfiguration?,
            ): ChatAgentExecution = object : ChatAgentExecution {
                override suspend fun send(
                    text: String,
                    fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                    rewindBeforeInvocationId: String?,
                ): Flow<ChatRunEvent> = flow { throw java.io.IOException("offline") }

                override suspend fun respondToToolConfirmation(
                    confirmationCallId: String,
                    confirmed: Boolean,
                ): Flow<ChatRunEvent> = flowOf(event())

                override suspend fun respondToInputRequest(
                    callId: String,
                    toolName: String,
                    value: String,
                ): Flow<ChatRunEvent> = flowOf(event())

            }
        }
        val fixture = fixture(configured = true, agentOverride = failingAgent)
        fixture.viewModel.send("你好")
        advanceUntilIdle()

        val failed = fixture.viewModel.uiState.value.failedTurn
        assertEquals(ChatTurnStatus.FAILED, failed?.status)
        assertTrue(failed?.canRetry == true)
        // 首轮在 ADK 事件回流前即失败，没有可回退的真实 invocation id。
        assertNull(failed?.rewindBeforeInvocationId)

        fixture.viewModel.onAction(ChatAction.RetryFailedTurn)
        advanceUntilIdle()

        val userCount = fixture.viewModel.uiState.value.messages.count { it.role == MessageRole.User }
        assertEquals(1, userCount)
        val retried = fixture.viewModel.uiState.value.failedTurn
        // 重试仍失败且未产生事件，同样没有可回退的真实 invocation id。
        assertNull(retried?.rewindBeforeInvocationId)
    }

    @Test
    fun failedOfficialRewind_keepsThePreviousInvocationBoundary() = runTest {
        var callCount = 0
        val failingAgent = object : ChatAgentRepository {
            override suspend fun createExecution(
                sessionId: String,
                selection: ModelSelection,
                toolConfiguration: ConversationToolConfiguration?,
            ): ChatAgentExecution = object : ChatAgentExecution {
                override suspend fun send(
                    text: String,
                    fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                    rewindBeforeInvocationId: String?,
                ): Flow<ChatRunEvent> = flow {
                    callCount++
                    if (callCount == 1) {
                        // 先流出带真实 ADK invocationId 的部分回答，再失败，从而建立待回退边界。
                        emit(event(partial = true, turnComplete = false))
                        throw java.io.IOException("offline")
                    }
                    throw ChatSessionRewindException(IllegalStateException("missing invocation"))
                }

                override suspend fun respondToToolConfirmation(
                    confirmationCallId: String,
                    confirmed: Boolean,
                ): Flow<ChatRunEvent> = flowOf(event())

                override suspend fun respondToInputRequest(
                    callId: String,
                    toolName: String,
                    value: String,
                ): Flow<ChatRunEvent> = flowOf(event())

            }
        }
        val fixture = fixture(configured = true, agentOverride = failingAgent)
        fixture.viewModel.send("你好")
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.RetryFailedTurn)
        advanceUntilIdle()

        val retried = fixture.viewModel.uiState.value.failedTurn
        // rewind 失败时保留上一轮真实 invocation 边界，不被错误地替换。
        assertEquals("invocation-1", retried?.rewindBeforeInvocationId)
    }

    @Test
    fun manualStop_keepsTurnRetryableWithPartialOutput() = runTest {
        val hangingAgent = object : ChatAgentRepository {
            override suspend fun createExecution(
                sessionId: String,
                selection: ModelSelection,
                toolConfiguration: ConversationToolConfiguration?,
            ): ChatAgentExecution = object : ChatAgentExecution {
                override suspend fun send(
                    text: String,
                    fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                    rewindBeforeInvocationId: String?,
                ): Flow<ChatRunEvent> = flow {
                    emit(event(partial = true, turnComplete = false))
                    awaitCancellation()
                }

                override suspend fun respondToToolConfirmation(
                    confirmationCallId: String,
                    confirmed: Boolean,
                ): Flow<ChatRunEvent> = flowOf(event())

                override suspend fun respondToInputRequest(
                    callId: String,
                    toolName: String,
                    value: String,
                ): Flow<ChatRunEvent> = flowOf(event())

            }
        }
        val fixture = fixture(configured = true, agentOverride = hangingAgent)
        fixture.viewModel.send("你好")
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.isAgentRunning)

        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        // 手动停止的轮次同样可以编辑/重试，且快照保留已生成的部分回答。
        val stopped = fixture.viewModel.uiState.value.failedTurn
        assertEquals(ChatTurnStatus.FAILED, stopped?.status)
        assertTrue(stopped?.canRetry == true)
        assertEquals(2, stopped?.messages?.size)
        assertEquals("回复", stopped?.messages?.last()?.textParts?.single()?.text)
    }

    @Test
    fun switchingSessionCancelsFailedTurnEditingImmediately() = runTest {
        val fixture = fixture(configured = true, agentOverride = alwaysFailingAgent())
        fixture.viewModel.send("待编辑")
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.EditFailedTurn)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.editingFailedTurn)

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-2"))

        assertEquals(FailedTurnRecoveryState.Idle, fixture.viewModel.uiState.value.failedTurnRecovery)
        assertEquals(MessageData(), fixture.viewModel.uiState.value.composerSeed)
    }

    @Test
    fun startingNewConversationCancelsFailedTurnEditingImmediately() = runTest {
        val fixture = fixture(configured = true, agentOverride = alwaysFailingAgent())
        fixture.viewModel.send("待编辑")
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.EditFailedTurn)
        advanceUntilIdle()

        fixture.viewModel.onAction(ChatAction.NewConversation)

        assertEquals(FailedTurnRecoveryState.Idle, fixture.viewModel.uiState.value.failedTurnRecovery)
        assertEquals(MessageData(), fixture.viewModel.uiState.value.composerSeed)
    }

    @Test
    fun leavingChatCancelsFailedTurnEditingImmediately() = runTest {
        val fixture = fixture(configured = true, agentOverride = alwaysFailingAgent())
        fixture.viewModel.send("待编辑")
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.EditFailedTurn)
        advanceUntilIdle()

        fixture.viewModel.onAction(ChatAction.LeaveChat)

        assertEquals(FailedTurnRecoveryState.Idle, fixture.viewModel.uiState.value.failedTurnRecovery)
        assertEquals(MessageData(), fixture.viewModel.uiState.value.composerSeed)
    }

    @Test
    fun hasToolCallsAfter_onlyCountsToolCallsWithinCurrentTurn() {
        val toolMessage = Messages.fromAssistant().copy(
            functionCalls = listOf(FunctionCallView(id = "c1", name = "tool", argsSummary = "{}")),
        )
        val firstUser = Messages.fromUser("first")
        val secondUser = Messages.fromUser("second")

        // 历史轮的工具调用不计入本轮，避免无工具的失败轮误报“重复执行”确认框。
        assertFalse(listOf(firstUser, toolMessage).hasToolCallsAfter(secondUser.id))
        // 用户消息之后的本轮工具调用计入。
        assertTrue(listOf(secondUser, toolMessage).hasToolCallsAfter(secondUser.id))
        // 找不到本轮用户消息时不误报。
        assertFalse(listOf(firstUser, toolMessage).hasToolCallsAfter("missing-id"))
    }

    @Test
    fun completedReply_autoSpeaksLatestAssistantMessageWhenEnabled() = runTest {
        val fixture = fixture(configured = true)
        assertTrue(fixture.viewModel.uiState.value.autoSpeakEnabled)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        val assistant = fixture.viewModel.uiState.value.messages[1]
        verify { fixture.playback.play(assistant.id, "回复") }
    }

    @Test
    fun completedReply_skipsAutoSpeakWhenGloballyDisabled() = runTest {
        val fixture = fixture(configured = true)
        fixture.speechSettings.setAutoSpeakEnabled(false)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.autoSpeakEnabled)
        verify(exactly = 0) { fixture.playback.play(any(), any()) }
    }

    @Test
    fun toggleAutoSpeakAction_flipsGlobalSwitch() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.onAction(ChatAction.ToggleAutoSpeak)
        advanceUntilIdle()
        assertFalse(fixture.speechSettings.autoSpeakEnabled.value)
        assertFalse(fixture.viewModel.uiState.value.autoSpeakEnabled)

        fixture.viewModel.onAction(ChatAction.ToggleAutoSpeak)
        advanceUntilIdle()
        assertTrue(fixture.speechSettings.autoSpeakEnabled.value)
        assertTrue(fixture.viewModel.uiState.value.autoSpeakEnabled)
    }

    @Test
    fun sameSessionBusyRejectsChatSendWithoutLeavingRunningState() = runTest {
        val gate = FakeAgentRuntimeGate().apply {
            acquireException = AgentSessionBusyException("session-1")
        }
        val fixture = fixture(configured = true, agentRuntimeGate = gate)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun wakeCommandCaptureDoesNotReplaceCurrentChatComposer() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-1"))
        advanceUntilIdle()
        val stateBeforeWake = fixture.viewModel.uiState.value

        fixture.voiceWake.value = VoiceWakeState(
            status = VoiceWakeStatus.CapturingCommand,
        )
        advanceUntilIdle()

        assertEquals(stateBeforeWake, fixture.viewModel.uiState.value)
    }

    @Test
    fun currentChatVisibilityIsForwardedToVoiceWakeRuntime() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.setCurrentChatVisible(true)
        fixture.viewModel.setCurrentChatVisible(false)

        verifyOrder {
            fixture.voiceWakeRepository.setCurrentChatVisible(true)
            fixture.voiceWakeRepository.setCurrentChatVisible(false)
        }
    }

    @Test
    fun completeEventFunctionResponsesMergeIntoStreamingMessage() = runTest {
        // 复刻真机丢失轮播的时序：SSE 下工具调用经 partial 增量合入仍在流式中的消息，
        // 工具结果只随随后的完整事件（partial=false）到达。收尾合并不得丢弃该响应，
        // 否则 MessageBubble 的本地文件轮播/远程图片轮播永远没有数据。
        val streamedCall = ChatFunctionCall(id = "call-1", name = "search_media_files", args = emptyMap())
        val response = ChatFunctionResponse(
            id = "call-1",
            name = "search_media_files",
            localFileSearchResult = LocalFileSearchResult(
                query = "screen",
                files = listOf(
                    LocalFileReference(
                        displayName = "screen.png",
                        mimeType = "image/png",
                        sizeBytes = 1L,
                        modifiedTimeMillis = 2L,
                        category = "image",
                        contentUri = "content://media/screen",
                    ),
                ),
            ),
        )
        val events = listOf(
            event(text = "正在搜索", partial = true).copy(
                functionCalls = listOf(streamedCall),
            ),
            event(text = "", partial = false).copy(
                functionResponses = listOf(response),
            ),
        )
        val fixture = fixture(configured = true, events = events)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        val assistant = fixture.viewModel.uiState.value.messages.last { it.role == MessageRole.Assistant }
        assertEquals("screen.png", assistant.functionResponses.single().localFileSearchResult?.files?.single()?.displayName)
        assertEquals("search_media_files", assistant.functionCalls.single().name)
    }

    @Test
    fun completeEventDoesNotDuplicateCallsAlreadyStreamedIntoPartialMessage() = runTest {
        // 完整事件可能重复携带 partial 阶段已合入的调用（SSE 聚合结果），按 (id, name) 去重。
        val streamedCall = ChatFunctionCall(id = "call-1", name = "search_media_files", args = emptyMap())
        val events = listOf(
            event(text = "正在搜索", partial = true).copy(
                functionCalls = listOf(streamedCall),
            ),
            event(text = "", partial = false).copy(
                functionCalls = listOf(streamedCall),
            ),
        )
        val fixture = fixture(configured = true, events = events)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        val assistant = fixture.viewModel.uiState.value.messages.last { it.role == MessageRole.Assistant }
        assertEquals(1, assistant.functionCalls.size)
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
            fixture.viewModel.send("你好")
            advanceUntilIdle()

            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.ConfigureChatModel),
                awaitItem(),
            )
            assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
            coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
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
                true,
                match { configuration ->
                    configuration.enabledMcpServerIds == setOf("enabled-mcp")
                },
            )
        }
    }

    @Test
    fun sendReloadsPersistedToolConfigurationAfterAgentImportsMcpServers() = runTest {
        val fixture = fixture(configured = true)
        val beforeImport = ConversationToolConfiguration(
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
        coEvery { fixture.conversations.lastConversationId() } returns "session-1"

        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.send("use the new tools")
        advanceUntilIdle()

        coVerify {
            fixture.agent.createExecution(
                "session-1",
                any(),
                match {
                    it.enabledMcpServerIds == afterImport.enabledMcpServerIds
                },
            )
            fixture.execution.send("use the new tools", any(), any())
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

        verify { fixture.appearance.setDarkThemeOverride(true) }
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
    fun externalWriteToCachedBackgroundConversationReloadsWhenOpened() = runTest {
        val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
        val fixture = fixture(configured = true, contentRevisions = revisions)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
        advanceUntilIdle()

        val external = Messages.fromAssistant().copy(textParts = listOf(
            github.ponyhuang.gimi.domain.conversation.model.TextPart(text = "外部回答"),
        ))
        coEvery { fixture.conversations.loadMessages("session-a") } returns listOf(external)
        revisions.value = mapOf("session-a" to 1L)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.messages.isEmpty())

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        assertEquals(listOf(external), fixture.viewModel.uiState.value.messages)
    }

    @Test
    fun externalWriteDuringHistoryReloadIsNotAcknowledgedByOlderSnapshot() = runTest {
        val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
        val fixture = fixture(configured = true, contentRevisions = revisions)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        val old = Messages.fromUser(text = "旧快照")
        val latest = Messages.fromUser(text = "最新快照")
        var loads = 0
        coEvery { fixture.conversations.loadMessages("session-a") } coAnswers {
            loads++
            if (loads == 1) {
                revisions.value = mapOf("session-a" to 2L)
                listOf(old)
            } else {
                listOf(latest)
            }
        }
        revisions.value = mapOf("session-a" to 1L)
        advanceUntilIdle()
        assertEquals(listOf(latest), fixture.viewModel.uiState.value.messages)
        assertEquals(2, loads)
    }

    @Test
    fun externalRevisionWaitsUntilForegroundRunFinishes() = runTest {
        val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent, contentRevisions = revisions)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        fixture.viewModel.send("问题")
        runCurrent()
        agent.emit("session-a", event(text = "流式回答"))
        runCurrent()
        val persisted = Messages.fromUser(text = "更新后的历史")
        coEvery { fixture.conversations.loadMessages("session-a") } returns listOf(persisted)
        revisions.value = mapOf("session-a" to 1L)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.messages.any {
            it.textParts.any { part -> part.text == "流式回答" }
        })
        agent.complete("session-a")
        advanceUntilIdle()
        assertEquals(listOf(persisted), fixture.viewModel.uiState.value.messages)
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

        fixture.viewModel.send("ask-a")
        runCurrent()
        agent.emit("session-a", event(text = "answer-a", invocationId = "inv-a"))
        runCurrent()

        fixture.viewModel.onAction(ChatAction.NewConversation)
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
            fixture.viewModel.send("task-$index")
            runCurrent()
            fixture.viewModel.onAction(ChatAction.NewConversation)
            advanceUntilIdle()
        }

        fixture.viewModel.effects.test {
            fixture.viewModel.send("task-4")
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
        fixture.viewModel.send("tool-a")
        runCurrent()
        agent.emit(
            "session-a",
            confirmationEvent(confirmation("confirm-a", "compose_message", emptyMap())),
        )
        runCurrent()

        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        fixture.viewModel.send("task-b")
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
        fixture.viewModel.send("执行工具")
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
        )
        runCurrent()
        // 流进行中（确认事件已到、run 流未结束）卡片也不得出现，否则会闪一下再消失。
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        agent.complete("session-1")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        assertEquals(listOf("confirm-1" to true), agent.confirmationResponses)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
    }

    @Test
    fun inputRequestCaptureAndUserReplyResumeTheRun() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.viewModel.send("问个问题")
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(
                ChatFunctionCall(
                    id = "input-call-1",
                    name = "get_user_choice",
                    args = mapOf("options" to listOf("A", "B")),
                    inputRequest = UserInputRequest(
                        callId = "input-call-1",
                        toolName = "get_user_choice",
                        kind = UserInputKind.CHOICE,
                        message = "选一个",
                        options = listOf("A", "B"),
                    ),
                ),
            ),
        )
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(listOf("input-call-1"), state.pendingInputRequests.map { it.callId })
        assertEquals(ConversationTaskStatus.WaitingForInput, state.conversationTaskStatuses["session-1"])
        // 输入请求挂起期间锁定普通发送，避免绕过 FunctionResponse 恢复协议。
        var rejection: ChatSubmissionResult? = null
        fixture.viewModel.send("先发别的") { rejection = it }
        assertEquals(ChatSubmissionResult.REJECTED, rejection)

        fixture.viewModel.onAction(ChatAction.RespondToInputRequest("input-call-1", "A"))
        advanceUntilIdle()

        assertEquals(listOf("input-call-1" to "A"), agent.inputResponses)
        assertTrue(fixture.viewModel.uiState.value.pendingInputRequests.isEmpty())
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        // ADK 不把用户 FunctionResponse 作为事件回流 —— 实时路径必须本地补响应消息，
        // 调用 chip 才能立即按 id 配对成 ✓ 而不是任务结束后误显 ✗。
        val responseMessage = fixture.viewModel.uiState.value.messages
            .single { it.functionResponses.any { response -> response.id == "input-call-1" } }
        assertEquals("input-response-input-call-1", responseMessage.id)
    }

    @Test
    fun pendingInputRequestSurvivesSessionSwitchAndReply() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(
            configured = true,
            agentOverride = agent,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.send("问个问题")
        runCurrent()
        agent.emit(
            "session-a",
            confirmationEvent(
                ChatFunctionCall(
                    id = "input-call-a",
                    name = "get_user_choice",
                    args = mapOf("options" to listOf("A", "B")),
                    inputRequest = UserInputRequest(
                        callId = "input-call-a",
                        toolName = "get_user_choice",
                        kind = UserInputKind.CHOICE,
                        message = "选一个",
                        options = listOf("A", "B"),
                    ),
                ),
            ),
        )
        runCurrent()
        agent.complete("session-a")
        advanceUntilIdle()
        assertEquals(
            listOf("input-call-a"),
            fixture.viewModel.uiState.value.pendingInputRequests.map { it.callId },
        )

        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.pendingInputRequests.isEmpty())

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        assertEquals(
            listOf("input-call-a"),
            fixture.viewModel.uiState.value.pendingInputRequests.map { it.callId },
        )

        fixture.viewModel.onAction(ChatAction.RespondToInputRequest("input-call-a", "A"))
        advanceUntilIdle()
        assertEquals(listOf("input-call-a" to "A"), agent.inputResponses)
        assertTrue(fixture.viewModel.uiState.value.pendingInputRequests.isEmpty())
    }

    @Test
    fun alwaysAllowedToolAutoApprovesConfirmationWithoutShowingCard() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.toolApproval.setAlwaysAllowed("compose_message")
        fixture.viewModel.send("执行工具")
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
        )
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        agent.complete("session-1")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
        assertEquals(listOf("confirm-1" to true), agent.confirmationResponses)
    }

    @Test
    fun mixedConfirmationQueuesOnlyUserApprovalToolForTheCard() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.toolApproval.setAlwaysAllowed("compose_message")
        fixture.viewModel.send("执行工具")
        runCurrent()
        agent.emit(
            "session-1",
            confirmationEvent(
                confirmation("confirm-1", "compose_message", emptyMap()),
                confirmation("confirm-2", "read_file", emptyMap()),
            ),
        )
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()

        // 白名单命中的 confirm-1 不进卡片，只有未放行的 confirm-2 等用户决策。
        assertEquals(
            listOf("confirm-2"),
            fixture.viewModel.uiState.value.pendingToolConfirmations.map { it.confirmationCallId },
        )

        fixture.viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed = false))
        advanceUntilIdle()

        assertEquals(
            listOf("confirm-2" to false, "confirm-1" to true),
            agent.confirmationResponses,
        )
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmations.isEmpty())
    }

    @Test
    fun alwaysAllowResponsePersistsToolToWhitelist() = runTest {
        val fixture = fixture(
            configured = true,
            events = listOf(
                confirmationEvent(confirmation("confirm-1", "compose_message", emptyMap())),
            ),
        )
        fixture.viewModel.send("执行工具")
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
        fixture.viewModel.send("执行工具")
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
        fixture.viewModel.send("first")
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

        fixture.viewModel.send("second")
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
        fixture.viewModel.send("running-a")
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
            every { supportedToolIds(any(), any()) } returns setOf("web_search")
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

    @Test
    fun confirmationResumeFailureKeepsTheOriginalTurnRetryable() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent)
        fixture.viewModel.send("工具请求")
        runCurrent()
        agent.emit("session-1", confirmationEvent(confirmation("confirm", "clock", emptyMap())))
        agent.complete("session-1")
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.pendingToolConfirmation != null)
        assertNull(fixture.viewModel.uiState.value.failedTurn)

        agent.resumeFailure = java.io.IOException("resume failed")
        fixture.viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed = true))
        advanceUntilIdle()

        val failed = fixture.viewModel.uiState.value.failedTurn
        assertTrue(failed?.canRetry == true)
        assertEquals("工具请求", failed?.userMessage?.textParts?.single()?.text)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
    }

    @Test
    fun errorEventKeepsTheTurnRetryableWithoutAThrownException() = runTest {
        val fixture = fixture(
            configured = true,
            events = listOf(event().copy(errorCode = "provider_error", errorMessage = "request failed")),
        )
        fixture.viewModel.send("问题")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.failedTurn?.canRetry == true)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
    }

    @Test
    fun submissionWaitsForAgentPreparationAndRejectsDuplicateSend() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val ready = CompletableDeferred<Unit>()
        coEvery { fixture.agent.createExecution(any(), any(), any()) } coAnswers {
            ready.await()
            fixture.execution
        }
        val results = mutableListOf<ChatSubmissionResult>()
        fixture.viewModel.send("第一条", onResult = results::add)
        runCurrent()
        assertTrue(results.isEmpty())
        assertFalse(fixture.viewModel.uiState.value.messages.any { it.role == MessageRole.User })
        fixture.viewModel.send("重复提交", onResult = results::add)
        assertEquals(listOf(ChatSubmissionResult.REJECTED), results)

        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(ChatSubmissionResult.REJECTED, ChatSubmissionResult.ACCEPTED), results)
        coVerify(exactly = 1) { fixture.execution.send("第一条", any(), any()) }
    }

    @Test
    fun failedPreparationDoesNotConsumeInputOrPublishAUserMessage() = runTest {
        val fixture = fixture(configured = true, attachmentReadFailure = java.io.IOException("missing"))
        val results = mutableListOf<ChatSubmissionResult>()
        fixture.viewModel.send("保留输入", onResult = results::add)
        advanceUntilIdle()
        assertEquals(listOf(ChatSubmissionResult.REJECTED), results)
        assertFalse(fixture.viewModel.uiState.value.messages.any { it.role == MessageRole.User })
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun configurationFailureRejectsInsteadOfReusingOldTools() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        coEvery { fixture.conversations.conversationToolConfiguration(any()) } throws java.io.IOException("configuration unavailable")
        val results = mutableListOf<ChatSubmissionResult>()
        fixture.viewModel.send("请求", onResult = results::add)
        advanceUntilIdle()
        assertEquals(listOf(ChatSubmissionResult.REJECTED), results)
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun cancellationDoesNotReleaseLeaseOrAcceptInputBeforePreparationReallyStops() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(configured = true, agentRuntimeGate = gate)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val ready = CompletableDeferred<Unit>()
        coEvery { fixture.agent.createExecution(any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { ready.await() }
            fixture.execution
        }
        val results = mutableListOf<ChatSubmissionResult>()
        fixture.viewModel.send("不能晚到后再发送", onResult = results::add)
        runCurrent()
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        runCurrent()
        assertEquals(0, gate.releaseCount)
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, gate.releaseCount)
        assertEquals(listOf(ChatSubmissionResult.REJECTED), results)
        coVerify(exactly = 0) { fixture.execution.send(any(), any(), any()) }
    }

    @Test
    fun navigationDuringPreparationRejectsTheOldSubmission() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val ready = CompletableDeferred<Unit>()
        coEvery { fixture.agent.createExecution(any(), any(), any()) } coAnswers {
            ready.await()
            fixture.execution
        }
        val results = mutableListOf<ChatSubmissionResult>()
        fixture.viewModel.send("旧页面请求", onResult = results::add)
        runCurrent()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-2"))
        runCurrent()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("session-2", fixture.viewModel.uiState.value.sessionId)
        assertEquals(listOf(ChatSubmissionResult.REJECTED), results)
        coVerify(exactly = 0) { fixture.execution.send(any(), any(), any()) }
    }

    private inner class ControllableAgent : ChatAgentRepository {
        var resumeFailure: Exception? = null
        val inputResponses = mutableListOf<Pair<String, String>>()
        val confirmationResponses = mutableListOf<Pair<String, Boolean>>()
        private val eventChannels = mutableMapOf<String, Channel<ChatRunEvent>>()

        private fun events(sessionId: String): Channel<ChatRunEvent> =
            eventChannels.getOrPut(sessionId) { Channel(Channel.UNLIMITED) }

        suspend fun emit(sessionId: String, event: ChatRunEvent) {
            events(sessionId).send(event)
        }

        fun complete(sessionId: String) {
            eventChannels.remove(sessionId)?.close()
        }

        override suspend fun createExecution(
            sessionId: String,
            selection: ModelSelection,
            toolConfiguration: ConversationToolConfiguration?,
        ): ChatAgentExecution = object : ChatAgentExecution {
            override suspend fun send(
                text: String,
                fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                rewindBeforeInvocationId: String?,
            ): Flow<ChatRunEvent> = events(sessionId).receiveAsFlow()

            override suspend fun respondToToolConfirmation(
                confirmationCallId: String,
                confirmed: Boolean,
            ): Flow<ChatRunEvent> {
                resumeFailure?.let { throw it }
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

            override suspend fun respondToInputRequest(
                callId: String,
                toolName: String,
                value: String,
            ): Flow<ChatRunEvent> {
                resumeFailure?.let { throw it }
                inputResponses += callId to value
                return flowOf(
                    event(
                        text = "input handled",
                        invocationId = "input-$sessionId",
                        partial = false,
                        turnComplete = true,
                    ),
                )
            }

        }
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
        agentRuntimeGate: FakeAgentRuntimeGate = FakeAgentRuntimeGate(),
        contentRevisions: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
        attachmentReadFailure: Exception? = null,
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
            every { conversationContentRevisions } returns contentRevisions
            coEvery { createConversation(any(), any(), any()) } returnsMany sessionIds
            coEvery { activateConversation(any(), any()) } returns ModelSelectionCodec.encode(selection)
            coEvery { loadMessages(any()) } returns emptyList()
            coEvery { conversationToolConfiguration(any()) } returns null
            coEvery { setConversationToolConfiguration(any(), any()) } returns true
        }
        val execution = mockk<ChatAgentExecution>(relaxed = true) {
            coEvery { send(any(), any(), any()) } returns flowOf(*events.toTypedArray())
        }
        val agent = agentOverride ?: mockk<ChatAgentRepository> {
            coEvery { createExecution(any(), any(), any()) } returns execution
        }
        val display = mockk<ChatDisplayRepository> {
            every { showToolActivity } returns MutableStateFlow(true)
        }
        val appearance = mockk<AppearanceRepository> {
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
        val speechSettings = FakeSpeechSettingsRepository()
        val defaultTools = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("enabled-mcp"),
            enabledOfficialFunctionIds = mapOf(
                "web_search" to setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            ),
        )
        val sessionResolver = object : ConversationSessionResolver {
            override suspend fun resolveCurrentOrCreate(): ConversationSessionSnapshot {
                val sessionId = conversations.lastConversationId()
                    ?.takeIf(String::isNotBlank)
                    ?: conversations.listConversations().firstOrNull()?.id
                    ?: return createAndActivate()
                return requireNotNull(activate(sessionId))
            }

            override suspend fun createAndActivate(): ConversationSessionSnapshot {
                check(configured) { "No available assistant model." }
                val sessionId = conversations.createConversation(
                    ModelSelectionCodec.encode(selection),
                    true,
                    defaultTools,
                )
                return ConversationSessionSnapshot(sessionId, selection, defaultTools)
            }

            override suspend fun activate(sessionId: String): ConversationSessionSnapshot? {
                if (sessionId.isBlank()) return null
                check(configured) { "No available assistant model." }
                if (conversations.loadMessages(sessionId) == null) return null
                conversations.activateConversation(sessionId, ModelSelectionCodec.encode(selection))
                return ConversationSessionSnapshot(
                    sessionId,
                    selection,
                    conversations.conversationToolConfiguration(sessionId) ?: defaultTools,
                )
            }

            override suspend fun resolveToolConfiguration(
                sessionId: String,
                modelSelection: ModelSelection,
            ): ConversationToolConfiguration =
                conversations.conversationToolConfiguration(sessionId) ?: defaultTools
        }
        val voiceWakeState = MutableStateFlow(VoiceWakeState())
        val voiceWake = mockk<VoiceWakeRepository>(relaxed = true) {
            every { state } returns voiceWakeState
        }
        val attachments = mockk<ChatAttachmentRepository> {
            coEvery { read(any(), any()) } coAnswers {
                attachmentReadFailure?.let { throw it }
                emptyList()
            }
            coEvery { validateSaved(any()) } returns Unit
            coEvery { createDrafts(any()) } returns emptyList()
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
                every { supportedToolIds(any(), any()) } returns setOf("web_search")
                coEvery { listFunctions(any()) } returns emptyList()
            }
        val toolApproval = FakeToolApprovalRepository()
        val prepareChatTurn = PrepareChatTurnUseCase(
            attachments = attachments,
        )
        return Fixture(
            viewModel = ChatViewModel(
                runner = agent,
                agentRuntimeGate = agentRuntimeGate,
                repository = conversations,
                sessionResolver = sessionResolver,
                modelServices = catalog,
                chatDisplayPreferences = display,
                appearanceRepository = appearance,
                toolApproval = toolApproval,
                speechRecognitionRepository = recognition,
                speechPlaybackController = playback,
                speechSettings = speechSettings,
                voiceWake = voiceWake,
                attachments = attachments,
                prepareChatTurn = prepareChatTurn,
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
            sessionResolver = sessionResolver,
            execution = execution,
            agent = agent,
            display = display,
            appearance = appearance,
            toolApproval = toolApproval,
            mcpRepository = mcpRepository,
            voiceWake = voiceWakeState,
            voiceWakeRepository = voiceWake,
            playback = playback,
            speechSettings = speechSettings,
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

    private fun alwaysFailingAgent(): ChatAgentRepository = object : ChatAgentRepository {
        override suspend fun createExecution(
            sessionId: String,
            selection: ModelSelection,
            toolConfiguration: ConversationToolConfiguration?,
        ): ChatAgentExecution = object : ChatAgentExecution {
            override suspend fun send(
                text: String,
                fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                rewindBeforeInvocationId: String?,
            ): Flow<ChatRunEvent> = flow { throw java.io.IOException("offline") }

            override suspend fun respondToToolConfirmation(
                confirmationCallId: String,
                confirmed: Boolean,
            ): Flow<ChatRunEvent> = flowOf(event())

            override suspend fun respondToInputRequest(
                callId: String,
                toolName: String,
                value: String,
            ): Flow<ChatRunEvent> = flowOf(event())

        }
    }

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
    )

    private data class Fixture(
        val viewModel: ChatViewModel,
        val conversations: ConversationRepository,
        val sessionResolver: ConversationSessionResolver,
        val execution: ChatAgentExecution,
        val agent: ChatAgentRepository,
        val display: ChatDisplayRepository,
        val appearance: AppearanceRepository,
        val toolApproval: FakeToolApprovalRepository,
        val mcpRepository: McpRepository,
        val voiceWake: MutableStateFlow<VoiceWakeState>,
        val voiceWakeRepository: VoiceWakeRepository,
        val playback: SpeechPlaybackRepository,
        val speechSettings: FakeSpeechSettingsRepository,
    )
}

private class FakeSpeechSettingsRepository : SpeechSettingsRepository {
    private val _autoSpeakEnabled = MutableStateFlow(true)
    override val autoSpeakEnabled: StateFlow<Boolean> = _autoSpeakEnabled.asStateFlow()

    override fun setAutoSpeakEnabled(enabled: Boolean) {
        _autoSpeakEnabled.value = enabled
    }
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
