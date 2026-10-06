package github.ponyhuang.gimi.feature.chat

import android.util.Log
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.Conversation
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
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatTurnUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatSendUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.ValidateChatAttachmentsUseCase
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.NoAvailableAssistantModelException
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
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.model.MemoryRuntimeFailure
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository
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
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
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
    fun documentTotalLimitRejectsBeforeReadingAttachmentsOrCreatingAnExecution() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val drafts = listOf(documentDraft("a.pdf", 26L * 1024 * 1024),
            documentDraft("b.pdf", 26L * 1024 * 1024))
        fixture.viewModel.effects.test {
            val results = mutableListOf<ChatSubmissionResult>()
            fixture.viewModel.send("文档请求", drafts, results::add)
            assertEquals(listOf(ChatSubmissionResult.REJECTED), results)
            assertEquals(ChatEffect.ShowNotice(ChatNotice.DocumentTotalSizeLimitExceeded), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { fixture.attachments.read(any(), any()) }
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun mixedAttachmentCategoriesRejectBeforePreparingTheSend() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val document = documentDraft("a.pdf", 100)
        val image = document.copy(reference = "/drafts/a.png", mimeType = "image/png",
            category = github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory.IMAGE)
        fixture.viewModel.effects.test {
            fixture.viewModel.send("混合附件", listOf(document, image))
            assertEquals(ChatEffect.ShowNotice(ChatNotice.MixedAttachmentCategories), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { fixture.agent.createExecution(any(), any(), any()) }
    }

    private fun fixtureWithExecutedToolFailure(): Fixture {
        val fixture = fixture(configured = true)
        coEvery { fixture.execution.send(any(), any(), any()) } returns flow {
            emit(event().copy(functionCalls = listOf(
                ChatFunctionCall(id = "executed-tool", name = "compose_message", args = emptyMap()),
            )))
            throw java.io.IOException("failed after executing tool")
        }
        return fixture
    }

    private fun documentDraft(name: String, sizeBytes: Long) =
        github.ponyhuang.gimi.domain.conversation.model.DraftAttachment(
            reference = "/drafts/$name", displayName = name, mimeType = "application/pdf",
            sizeBytes = sizeBytes,
            category = github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory.DOCUMENT,
        )

    @Test
    fun stoppedTurnRetryImmediatelyReconcilesWithSavedHistoryWithoutExternalRevision() = runTest {
        val fixture = fixture(configured = true)
        coEvery { fixture.execution.send(any(), any(), false) } returns flow {
            emit(event(text = "没", partial = true, turnComplete = false).copy(id = "stopped-partial"))
            awaitCancellation()
        }
        fixture.viewModel.send("你那死了")
        runCurrent()
        val user = fixture.viewModel.uiState.value.messages.first { it.role == MessageRole.User }
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.messages.any { message ->
            message.textParts.any { it.text == "没" }
        })
        val finalEvent = event(text = "没太看懂这句，我还在呢，一切正常。", partial = false, turnComplete = true)
            .copy(id = "resumed-final")
        coEvery { fixture.execution.send(any(), any(), true) } returns flowOf(finalEvent)
        val saved = Messages.fromAssistant(id = "saved-final").copy(
            textParts = listOf(TextPart(text = "没太看懂这句，我还在呢，一切正常。")),
        )
        coEvery { fixture.conversations.loadMessages("session-1") } returns listOf(user, saved)

        fixture.viewModel.onAction(ChatAction.RetryFailedTurn)
        advanceUntilIdle()

        assertEquals(listOf(user, saved), fixture.viewModel.uiState.value.messages)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        assertEquals(null, fixture.viewModel.uiState.value.failedTurn)
        assertEquals(0L, fixture.viewModel.runtimeFor("session-1").loadedContentRevision)
    }

    @Test
    fun failedStoppedTurnRetryKeepsTheRecoverablePartialWithoutReloadingAfterFailure() = runTest {
        val fixture = fixture(configured = true)
        coEvery { fixture.execution.send(any(), any(), false) } returns flow {
            emit(event(text = "没", partial = true, turnComplete = false))
            awaitCancellation()
        }
        fixture.viewModel.send("你好")
        runCurrent()
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()
        coEvery { fixture.execution.send(any(), any(), true) } returns flow { throw java.io.IOException("retry failed") }
        coEvery { fixture.conversations.loadMessages("session-1") } returns emptyList()

        fixture.viewModel.onAction(ChatAction.RetryFailedTurn)
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.failedTurn?.canRetry == true)
        assertTrue(fixture.viewModel.uiState.value.messages.any { message ->
            message.textParts.any { it.text == "没" }
        })
        // 重试准备阶段激活会话会读一次；失败收尾不能再次读取并覆盖部分输出。
        coVerify(exactly = 1) { fixture.conversations.loadMessages("session-1") }
    }

    @Test
    fun failedTurn_retry_reusesOriginalUserMessageWithoutDuplication() = runTest {
        val sentTexts = mutableListOf<String>()
        val failingAgent = object : ChatAgentRepository {
            override suspend fun createExecution(
                sessionId: String,
                selection: ModelSelection,
                toolConfiguration: ConversationToolConfiguration?,
            ): ChatAgentExecution = object : ChatAgentExecution {
                override suspend fun send(
                    text: String,
                    fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                    retry: Boolean,
                ): Flow<ChatRunEvent> = flow {
                    sentTexts += text
                    throw java.io.IOException("offline")
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

        val failed = fixture.viewModel.uiState.value.failedTurn
        assertEquals(ChatTurnStatus.FAILED, failed?.status)
        assertTrue(failed?.canRetry == true)

        fixture.viewModel.onAction(ChatAction.RetryFailedTurn)
        advanceUntilIdle()

        val userCount = fixture.viewModel.uiState.value.messages.count { it.role == MessageRole.User }
        assertEquals(1, userCount)
        assertEquals(listOf("你好", "你好"), sentTexts)
        assertTrue(fixture.viewModel.uiState.value.failedTurn?.canRetry == true)
    }

    @Test
    fun failedRetryWithToolCallsDirectlyResumesAndKeepsPartialOutput() = runTest {
        var callCount = 0
        val retries = mutableListOf<Boolean>()
        val failingAgent = object : ChatAgentRepository {
            override suspend fun createExecution(
                sessionId: String,
                selection: ModelSelection,
                toolConfiguration: ConversationToolConfiguration?,
            ): ChatAgentExecution = object : ChatAgentExecution {
                override suspend fun send(
                    text: String,
                    fileAttachments: List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>,
                    retry: Boolean,
                ): Flow<ChatRunEvent> = flow {
                    callCount++
                    retries += retry
                    if (callCount == 1) {
                        // 先流出带真实 ADK invocationId 的部分回答，再失败，供 SDK 恢复原执行。
                        emit(event(partial = true, turnComplete = false).copy(
                            functionCalls = listOf(ChatFunctionCall(
                                id = "call-1", name = "search", args = emptyMap(),
                            )),
                        ))
                        throw java.io.IOException("offline")
                    }
                    throw java.io.IOException("offline again")
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
        assertEquals(2, callCount)
        assertEquals(listOf(false, true), retries)
        assertEquals(1, retried?.messages?.count { it.role == MessageRole.User })
        assertTrue(retried?.messages?.any { message ->
            message.textParts.any { it.text == "回复" }
        } == true)
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
                    retry: Boolean,
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
    fun sendingSupplementAfterFailureAppendsANewUserMessage() = runTest {
        val fixture = fixture(configured = true, agentOverride = alwaysFailingAgent())
        fixture.viewModel.send("原问题")
        advanceUntilIdle()
        fixture.viewModel.send("补充说明")
        advanceUntilIdle()
        assertEquals(listOf("原问题", "补充说明"),
            fixture.viewModel.uiState.value.messages.filter { it.role == MessageRole.User }
                .map { it.textParts.single().text })
    }

    @Test
    fun completedReply_autoSpeaksLatestAssistantMessageWhenEnabled() = runTest {
        val fixture = fixture(configured = true)
        advanceUntilIdle()
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
    fun sendWithUnexpectedResolutionFailureSurfacesRealCauseNotModelNotice() = runTest {
        val failingResolver = mockk<ConversationSessionResolver>(relaxed = true) {
            coEvery { resolveCurrentOrCreate() } throws java.io.IOException("disk boom")
            coEvery { activate(any()) } throws java.io.IOException("disk boom")
        }
        val fixture = fixture(configured = true, sessionResolverOverride = failingResolver)

        fixture.viewModel.effects.test {
            fixture.viewModel.send("你好")
            advanceUntilIdle()

            assertEquals(
                ChatEffect.ShowNotice(ChatNotice.Message("disk boom")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resumeChatReseedsPartialChannelsWhenSessionIsActive() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        val sessionId = fixture.viewModel.uiState.value.sessionId
        val runtime = fixture.viewModel.runtimeFor(sessionId)
        runtime.isAgentRunning = true
        runtime.messages = listOf(
            Message(
                author = "Assistant",
                role = MessageRole.Assistant,
                textParts = listOf(TextPart(id = "part-1", text = "hello background", thought = false)),
                partial = true,
            ),
        )

        fixture.viewModel.onAction(ChatAction.ResumeChat)
        advanceUntilIdle()

        val channel = fixture.viewModel.partChannelFor("part-1")
        org.junit.Assert.assertNotNull(channel)
        assertEquals("hello background", channel?.tryReceive()?.getOrNull())
    }

    @Test
    fun resumeChatReloadsPersistedMessagesAfterBackgroundCompletion() = runTest {
        val persisted = Message(
            id = "complete-message",
            author = "Assistant",
            role = MessageRole.Assistant,
            textParts = listOf(TextPart(id = "complete-part", text = "完整的后台回复")),
            partial = false,
        )
        val fixture = fixture(configured = true)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        val sessionId = fixture.viewModel.uiState.value.sessionId
        val runtime = fixture.viewModel.runtimeFor(sessionId)
        runtime.isAgentRunning = false
        runtime.messages = listOf(persisted.copy(textParts = listOf(
            TextPart(id = "stale-part", text = "截断的后台回复"),
        )))
        coEvery { fixture.conversations.loadMessages(sessionId) } returns listOf(persisted)

        fixture.viewModel.onAction(ChatAction.ResumeChat)
        advanceUntilIdle()

        assertEquals(listOf(persisted), fixture.viewModel.uiState.value.messages)
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
    fun setThemeModeDelegatesToAppearanceRepository() = runTest {
        val fixture = fixture(configured = true)

        fixture.viewModel.onAction(ChatAction.SetThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        verify { fixture.appearance.setThemeMode(ThemeMode.DARK) }
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
    fun switchingToInactiveSessionReloadsAuthoritativeHistoryAfterBackgroundCompletion() = runTest {
        val fixture = fixture(
            configured = true,
            sessionIds = listOf("session-a", "session-b"),
        )
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()

        val stale = Messages.fromAssistant().copy(
            textParts = listOf(TextPart(text = "截断的后台回答")),
        )
        val complete = stale.copy(
            textParts = listOf(TextPart(text = "完整的后台回答，已经持久化完成。")),
        )
        val runtime = fixture.viewModel.runtimeFor("session-a")
        runtime.isLoaded = true
        runtime.isAgentRunning = false
        runtime.messages = listOf(stale)
        coEvery { fixture.conversations.loadMessages("session-a") } returns listOf(complete)
        val previousScrollRequest = fixture.viewModel.uiState.value.scrollToLatestRequest

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()

        assertEquals(listOf(complete), fixture.viewModel.uiState.value.messages)
        assertTrue(
            fixture.viewModel.uiState.value.scrollToLatestRequest > previousScrollRequest,
        )
    }

    @Test
    fun concurrentBackgroundCompletionsKeepDistinctNotificationTaskIds() = runTest {
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
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        fixture.viewModel.send("ask-b")
        runCurrent()

        agent.complete("session-a")
        agent.complete("session-b")
        advanceUntilIdle()

        verify(exactly = 1) {
            fixture.appNotificationManager.notifyTaskCompleted("session-a")
        }
        verify(exactly = 1) {
            fixture.appNotificationManager.notifyTaskCompleted("session-b")
        }
    }

    @Test
    fun supersededHistoryLoadCannotReplaceTheNewlySelectedConversation() = runTest {
        val fixture = fixture(configured = true)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val late = Messages.fromUser(text = "旧会话晚到的历史")
        val current = Messages.fromUser(text = "当前会话历史")
        coEvery { fixture.conversations.loadMessages("session-a") } coAnswers {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                listOf(late)
            }
        }
        coEvery { fixture.conversations.loadMessages("session-b") } returns listOf(current)

        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        runCurrent()
        started.await()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals("session-b", fixture.viewModel.uiState.value.sessionId)
        assertEquals(listOf(current), fixture.viewModel.uiState.value.messages)
        assertFalse(fixture.viewModel.uiState.value.isInitializing)
        assertFalse(fixture.viewModel.runtimeFor("session-a").isLoaded)
    }

    @Test
    fun staleReloadCannotOverwriteATurnThatCompletedWhileItWasReading() = runTest {
        val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
        val fixture = fixture(configured = true, contentRevisions = revisions)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val stale = Messages.fromUser(text = "发送前的旧历史")
        coEvery { fixture.conversations.loadMessages("session-a") } coAnswers {
            started.complete(Unit)
            release.await()
            listOf(stale)
        }
        revisions.value = mapOf("session-a" to 1L)
        runCurrent()
        started.await()
        // 发送准备读取当前 runtime 快照；测试模拟它已包含这次外部写入。
        fixture.viewModel.runtimeFor("session-a").loadedContentRevision = 1L
        fixture.viewModel.send("新一轮")
        runCurrent()
        val completed = fixture.viewModel.uiState.value.messages
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        assertTrue(completed.any { it.role == MessageRole.Assistant })

        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(completed, fixture.viewModel.uiState.value.messages)
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
    fun queuedConfirmationsKeepOneLeaseUntilTheFinalResponse() = runTest {
        val agent = ControllableAgent()
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(configured = true, agentOverride = agent, agentRuntimeGate = gate)
        fixture.viewModel.send("execute tools")
        runCurrent()
        agent.emit("session-1", confirmationEvent(
            confirmation("confirm-a", "compose_message", emptyMap()),
            confirmation("confirm-b", "read_file", emptyMap()),
        ))
        runCurrent()
        agent.complete("session-1")
        advanceUntilIdle()
        assertEquals(1, gate.acquisitions.size)
        assertEquals(0, gate.releaseCount)

        fixture.viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed = true))
        advanceUntilIdle()
        assertEquals("confirm-b", fixture.viewModel.uiState.value.pendingToolConfirmation?.confirmationCallId)
        assertEquals(1, gate.acquisitions.size)
        assertEquals(0, gate.releaseCount)

        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()
        assertEquals(listOf("confirm-a" to true, "confirm-b" to false), agent.confirmationResponses)
        assertEquals(1, gate.acquisitions.size)
        assertEquals(1, gate.releaseCount)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
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
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(configured = true, agentOverride = agent, agentRuntimeGate = gate)
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

        assertEquals(1, gate.acquisitions.size)
        assertEquals(0, gate.releaseCount)
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()
        assertEquals(0, gate.releaseCount)
        assertTrue(agent.inputResponses.isEmpty())

        val state = fixture.viewModel.uiState.value
        assertEquals(listOf("input-call-1"), state.pendingInputRequests.map { it.callId })
        assertEquals(ConversationTaskStatus.WaitingForInput, state.conversationTaskStatuses["session-1"])
        // 输入请求挂起期间锁定普通发送，避免绕过 FunctionResponse 恢复协议。
        var rejection: ChatSubmissionResult? = null
        fixture.viewModel.send("先发别的") { rejection = it }
        assertEquals(ChatSubmissionResult.REJECTED, rejection)

        fixture.viewModel.onAction(ChatAction.RespondToInputRequest("input-call-1", "A"))
        advanceUntilIdle()

        assertEquals(1, gate.acquisitions.size)
        assertEquals(1, gate.releaseCount)
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
    fun officialFunctionsExpandTheMarkerAndReuseLoadedDescriptors() = runTest {
        val catalog = toolFunctionCatalog()
        val fixture = fixture(configured = true, officialCatalogOverride = catalog)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        assertEquals(setOf("search", "read"), fixture.viewModel.uiState.value.toolConfiguration
            ?.enabledOfficialFunctionIds("web_search"))
        coVerify { fixture.conversations.setConversationToolConfiguration("session-1", match {
            it.enabledOfficialFunctionIds("web_search") == setOf("search", "read")
        }) }
        fixture.viewModel.onAction(ChatAction.LoadOfficialToolFunctions("web_search"))
        advanceUntilIdle()
        coVerify(exactly = 1) { catalog.listFunctions("web_search") }
    }

    @Test
    fun failedOfficialFunctionLoadCanBeRetriedExplicitly() = runTest {
        val catalog = toolFunctionCatalog()
        var attempts = 0
        coEvery { catalog.listFunctions("web_search") } coAnswers {
            if (++attempts == 1) throw java.io.IOException("catalog offline")
            toolFunctions()
        }
        val fixture = fixture(configured = true, officialCatalogOverride = catalog)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()

        val failed = fixture.viewModel.uiState.value.officialToolDescriptors.single()
        assertEquals("catalog offline", failed.loadError)
        assertFalse(failed.isLoadingFunctions)
        fixture.viewModel.onAction(ChatAction.LoadOfficialToolFunctions("web_search"))
        advanceUntilIdle()
        val recovered = fixture.viewModel.uiState.value.officialToolDescriptors.single()
        assertNull(recovered.loadError)
        assertEquals(toolFunctions(), recovered.functions)
        assertEquals(setOf("search", "read"), fixture.viewModel.uiState.value.toolConfiguration
            ?.enabledOfficialFunctionIds("web_search"))
        assertEquals(2, attempts)
    }

    @Test
    fun officialFunctionSelectionPersistsWithoutReloadingTheCatalog() = runTest {
        val catalog = toolFunctionCatalog()
        val fixture = fixture(configured = true, officialCatalogOverride = catalog)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.SetOfficialFunctionEnabled(
            toolId = "web_search", functionId = "search", enabled = false,
            supportedFunctionIds = setOf("search", "read"),
        ))
        advanceUntilIdle()
        assertEquals(setOf("read"), fixture.viewModel.uiState.value.toolConfiguration
            ?.enabledOfficialFunctionIds("web_search"))
        coVerify { fixture.conversations.setConversationToolConfiguration("session-1", match {
            it.enabledOfficialFunctionIds("web_search") == setOf("read")
        }) }
        coVerify(exactly = 1) { catalog.listFunctions("web_search") }
    }

    @Test
    fun switchingSessionsReusesTheDirectoryButExpandsEachSessionsConfiguration() = runTest {
        val catalog = toolFunctionCatalog()
        val fixture = fixture(configured = true, sessionIds = listOf("session-a", "session-b"),
            officialCatalogOverride = catalog)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        assertEquals("session-b", fixture.viewModel.uiState.value.sessionId)
        for (sessionId in listOf("session-a", "session-b")) {
            coVerify { fixture.conversations.setConversationToolConfiguration(sessionId, match {
                it.enabledOfficialFunctionIds("web_search") == setOf("search", "read")
            }) }
        }
        coVerify(exactly = 1) { catalog.listFunctions("web_search") }
    }

    @Test
    fun toolConfigurationWriteFailureKeepsThePreviousSelectionAndCanBeDismissed() = runTest {
        val fixture = fixture(configured = true, officialCatalogOverride = toolFunctionCatalog())
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        val before = fixture.viewModel.uiState.value.toolConfiguration
        coEvery { fixture.conversations.setConversationToolConfiguration(any(), any()) } returns false
        fixture.viewModel.onAction(ChatAction.SetMcpServerEnabled("extra-mcp", true))
        advanceUntilIdle()
        assertEquals(before, fixture.viewModel.uiState.value.toolConfiguration)
        assertTrue(fixture.viewModel.uiState.value.hasToolConfigurationError)
        fixture.viewModel.onAction(ChatAction.ClearToolConfigurationError)
        assertFalse(fixture.viewModel.uiState.value.hasToolConfigurationError)
    }

    @Test
    fun directoryLoadFinishingAfterASessionSwitchExpandsTheVisibleSessionsMarker() = runTest {
        val response = CompletableDeferred<List<OfficialToolFunction>>()
        val catalog = toolFunctionCatalog()
        coEvery { catalog.listFunctions("web_search") } coAnswers { response.await() }
        val fixture = fixture(configured = true, sessionIds = listOf("session-a", "session-b"),
            officialCatalogOverride = catalog)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.officialToolDescriptors.single().isLoadingFunctions)
        fixture.viewModel.onAction(ChatAction.NewConversation)
        runCurrent()
        response.complete(toolFunctions())
        advanceUntilIdle()
        assertEquals("session-b", fixture.viewModel.uiState.value.sessionId)
        assertEquals(setOf("search", "read"), fixture.viewModel.uiState.value.toolConfiguration
            ?.enabledOfficialFunctionIds("web_search"))
        assertEquals(setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            fixture.viewModel.runtimeFor("session-a").toolConfiguration?.enabledOfficialFunctionIds("web_search"))
        coVerify(exactly = 1) { catalog.listFunctions("web_search") }
    }

    private fun toolFunctions() = listOf(
        OfficialToolFunction("search", "Search", "Search pages"),
        OfficialToolFunction("read", "Read", "Read pages"),
    )

    private fun toolFunctionCatalog(): OfficialToolFunctionCatalog = mockk(relaxed = true) {
        every { availableTools(any(), any()) } returns listOf(OfficialToolAvailability("web_search", "service"))
        coEvery { listFunctions("web_search") } returns toolFunctions()
    }

    @Test
    fun cancelledOfficialFunctionLoadDoesNotSwallowCancellationException() = runTest {
        val catalog = mockk<OfficialToolFunctionCatalog>(relaxed = true) {
            every { availableTools(any(), any()) } returns listOf(
                OfficialToolAvailability("web_search", "service"),
            )
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
        coVerify(exactly = 1) { catalog.listFunctions("web_search") }
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
    fun successfulSendWithDraftAttachmentsDeliversAcceptedReceipt() = runTest {
        val fixture = fixture(configured = true)
        val drafts = listOf(
            github.ponyhuang.gimi.domain.conversation.model.DraftAttachment(
                reference = "/drafts/doc.pdf",
                displayName = "doc.pdf",
                mimeType = "application/pdf",
                sizeBytes = 128,
                category = github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory.DOCUMENT,
            ),
        )
        val results = mutableListOf<ChatSubmissionResult>()
        val archived = github.ponyhuang.gimi.domain.conversation.model.FileAttachment.fromBytes(
            "application/pdf", byteArrayOf(1, 2, 3), "doc.pdf",
        )
        coEvery { fixture.attachments.read("session-1", drafts) } returns listOf(archived)
        fixture.viewModel.send("带附件的提问", drafts, results::add)
        advanceUntilIdle()
        assertEquals(listOf(ChatSubmissionResult.ACCEPTED), results)
        coVerify(exactly = 1) { fixture.attachments.read("session-1", drafts) }
        coVerify(exactly = 1) { fixture.execution.send("带附件的提问", listOf(archived), false) }
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
                retry: Boolean,
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

    @Test
    fun batchDeletionRequiresConfirmationAndDeletesSelectedConversationsWithTheirAttachments() = runTest {
        val fixture = fixture(configured = true)
        fixture.setRecentConversations("first", "second", "unselected")
        advanceUntilIdle()
        fixture.recent(RecentConversationsAction.StartSelection("first"))
        fixture.recent(RecentConversationsAction.ToggleSelection("second"))
        fixture.recent(RecentConversationsAction.ConfirmDeletion)
        advanceUntilIdle()
        coVerify(exactly = 0) { fixture.conversations.deleteConversation(any()) }
        fixture.recent(RecentConversationsAction.RequestDeletion)
        fixture.recent(RecentConversationsAction.ConfirmDeletion)
        fixture.recent(RecentConversationsAction.ConfirmDeletion)
        advanceUntilIdle()
        coVerify(exactly = 1) { fixture.conversations.deleteConversation("first") }
        coVerify(exactly = 1) { fixture.conversations.deleteConversation("second") }
        coVerify(exactly = 0) { fixture.conversations.deleteConversation("unselected") }
        coVerify(exactly = 1) { fixture.attachments.deleteSession("first") }
        coVerify(exactly = 1) { fixture.attachments.deleteSession("second") }
        assertFalse(fixture.viewModel.uiState.value.recentConversations.isSelecting)
        assertFalse(fixture.viewModel.uiState.value.recentConversations.isDeleting)
    }

    @Test
    fun batchDeletionContinuesAfterFailureAndKeepsFailedSelectionForRetry() = runTest {
        val fixture = fixture(configured = true)
        fixture.setRecentConversations("failed", "success")
        advanceUntilIdle()
        coEvery { fixture.conversations.deleteConversation("failed") } throws java.io.IOException("storage failed")
        fixture.recent(RecentConversationsAction.StartSelection("failed"))
        fixture.recent(RecentConversationsAction.ToggleSelection("success"))
        fixture.recent(RecentConversationsAction.RequestDeletion)
        fixture.viewModel.effects.test {
            fixture.recent(RecentConversationsAction.ConfirmDeletion)
            advanceUntilIdle()
            assertEquals(ChatEffect.ShowNotice(ChatNotice.ConversationDeleteFailed), awaitItem())
        }
        coVerify(exactly = 1) { fixture.conversations.deleteConversation("success") }
        coVerify(exactly = 0) { fixture.attachments.deleteSession("failed") }
        assertEquals(setOf("failed"), fixture.viewModel.uiState.value.recentConversations.selectedIds)
        assertFalse(fixture.viewModel.uiState.value.recentConversations.isDeleting)
    }

    @Test
    fun batchDeletionRechecksCurrentConversationAfterConfirmationWasOpened() = runTest {
        val fixture = fixture(configured = true, sessionIds = listOf("session-1"))
        fixture.setRecentConversations("session-1", "other")
        advanceUntilIdle()
        fixture.recent(RecentConversationsAction.StartSelection("session-1"))
        fixture.recent(RecentConversationsAction.ToggleSelection("other"))
        fixture.recent(RecentConversationsAction.RequestDeletion)
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        assertEquals("session-1", fixture.viewModel.uiState.value.sessionId)
        fixture.viewModel.effects.test {
            fixture.recent(RecentConversationsAction.ConfirmDeletion)
            advanceUntilIdle()
            assertEquals(ChatEffect.ShowNotice(ChatNotice.ActiveConversationDeleteBlocked), awaitItem())
        }
        coVerify(exactly = 0) { fixture.conversations.deleteConversation("session-1") }
        coVerify(exactly = 1) { fixture.conversations.deleteConversation("other") }
    }

    @Test
    fun cancellationDuringBatchDeletionPropagatesAndReleasesDeletingState() = runTest {
        val fixture = fixture(configured = true)
        fixture.setRecentConversations("first", "second")
        advanceUntilIdle()
        coEvery { fixture.conversations.deleteConversation("first") } throws CancellationException("cancelled")
        fixture.recent(RecentConversationsAction.StartSelection("first"))
        fixture.recent(RecentConversationsAction.ToggleSelection("second"))
        fixture.recent(RecentConversationsAction.RequestDeletion)
        fixture.recent(RecentConversationsAction.ConfirmDeletion)
        advanceUntilIdle()
        coVerify(exactly = 0) { fixture.conversations.deleteConversation("second") }
        assertFalse(fixture.viewModel.uiState.value.recentConversations.isDeleting)
        assertEquals(setOf("first", "second"), fixture.viewModel.uiState.value.recentConversations.selectedIds)
    }

    @Test
    fun batchDeletionRechecksBackgroundTaskStartedAfterSelection() = runTest {
        val agent = ControllableAgent()
        val fixture = fixture(configured = true, agentOverride = agent, sessionIds = listOf("session-a", "session-b"))
        fixture.setRecentConversations("session-a", "session-b")
        fixture.viewModel.onAction(ChatAction.RestoreOrCreateSession)
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.NewConversation)
        advanceUntilIdle()
        fixture.recent(RecentConversationsAction.StartSelection("session-a"))
        fixture.recent(RecentConversationsAction.RequestDeletion)
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        fixture.viewModel.send("运行中的任务")
        runCurrent()
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-b"))
        advanceUntilIdle()
        fixture.viewModel.effects.test {
            fixture.recent(RecentConversationsAction.ConfirmDeletion)
            advanceUntilIdle()
            assertEquals(ChatEffect.ShowNotice(ChatNotice.ActiveConversationDeleteBlocked), awaitItem())
        }
        coVerify(exactly = 0) { fixture.conversations.deleteConversation("session-a") }
        fixture.viewModel.onAction(ChatAction.SwitchSession("session-a"))
        advanceUntilIdle()
        fixture.viewModel.onAction(ChatAction.StopStreaming)
        advanceUntilIdle()
    }

    private fun Fixture.recent(action: RecentConversationsAction) {
        viewModel.onAction(ChatAction.RecentConversations(action))
    }

    private fun Fixture.setRecentConversations(vararg ids: String) {
        val flow = conversations.conversations as MutableStateFlow<List<Conversation>>
        flow.value = ids.map { Conversation(id = it, title = it) }
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
        sessionResolverOverride: ConversationSessionResolver? = null,
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
        val appearance = mockk<AppearanceRepository> {
            every { themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
            every { setThemeMode(any()) } returns Unit
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
        val sessionResolver = sessionResolverOverride ?: object : ConversationSessionResolver {
            override suspend fun resolveCurrentOrCreate(): ConversationSessionSnapshot {
                val sessionId = conversations.lastConversationId()
                    ?.takeIf(String::isNotBlank)
                    ?: conversations.listConversations().firstOrNull()?.id
                    ?: return createAndActivate()
                return requireNotNull(activate(sessionId))
            }

            override suspend fun createAndActivate(): ConversationSessionSnapshot {
                if (!configured) throw NoAvailableAssistantModelException()
                val sessionId = conversations.createConversation(
                    ModelSelectionCodec.encode(selection),
                    true,
                    defaultTools,
                )
                return ConversationSessionSnapshot(sessionId, selection, defaultTools)
            }

            override suspend fun activate(sessionId: String): ConversationSessionSnapshot? {
                if (sessionId.isBlank()) return null
                if (!configured) throw NoAvailableAssistantModelException()
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
        val attachments = mockk<ChatAttachmentRepository> {
            coEvery { read(any(), any()) } coAnswers {
                attachmentReadFailure?.let { throw it }
                emptyList()
            }
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
                every { availableTools(any(), any()) } returns listOf(
                    OfficialToolAvailability("web_search", "service"),
                )
                coEvery { listFunctions(any()) } returns emptyList()
            }
        val appNotificationManager = mockk<AppNotificationManager>(relaxed = true)
        val appUpdateRepository = mockk<github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository>(relaxed = true)
        every { appUpdateRepository.hasUnseenUpdate } returns MutableStateFlow(false)
        val toolApproval = FakeToolApprovalRepository()
        val prepareChatTurn = PrepareChatTurnUseCase(
            attachments = attachments,
        )
        return Fixture(
            viewModel = ChatViewModel(
                agentRuntimeGate = agentRuntimeGate,
                repository = conversations,
                sessionResolver = sessionResolver,
                modelServices = catalog,
                appearanceRepository = appearance,
                toolApproval = toolApproval,
                speechRecognitionRepository = recognition,
                speechPlaybackController = playback,
                speechSettings = speechSettings,
                attachments = attachments,
                prepareChatSend = PrepareChatSendUseCase(sessionResolver, conversations, prepareChatTurn, agent),
                validateChatAttachments = ValidateChatAttachmentsUseCase(),
                toolAuthorization = toolAuthorization,
                mcpRepository = mcpRepository,
                mcpSkipReporter = mockk {
                    every { skipped } returns MutableStateFlow(emptyList())
                },
                officialFunctionCatalog = officialFunctionCatalog,
                memoryRuntimeStatus = mockk<MemoryRuntimeStatus>(relaxed = true) {
                    every { failures } returns memoryFailures
                },
                appNotificationManager = appNotificationManager,
                appUpdateRepository = appUpdateRepository,
            ),
            conversations = conversations,
            sessionResolver = sessionResolver,
            execution = execution,
            attachments = attachments,
            agent = agent,
            appearance = appearance,
            toolApproval = toolApproval,
            mcpRepository = mcpRepository,
            playback = playback,
            speechSettings = speechSettings,
            appNotificationManager = appNotificationManager,
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
                retry: Boolean,
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
        confirmationRequest = ToolConfirmationRequest(
            originalCallId = "original-$id",
            toolName = toolName,
            args = args,
        ),
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

    /** ChatViewModel 测试依赖集合；attachments 用于验证草稿读取和归档载荷传递。 */
    private data class Fixture(
        val viewModel: ChatViewModel,
        val conversations: ConversationRepository,
        val sessionResolver: ConversationSessionResolver,
        val execution: ChatAgentExecution,
        val attachments: ChatAttachmentRepository,
        val agent: ChatAgentRepository,
        val appearance: AppearanceRepository,
        val toolApproval: FakeToolApprovalRepository,
        val mcpRepository: McpRepository,
        val playback: SpeechPlaybackRepository,
        val speechSettings: FakeSpeechSettingsRepository,
        val appNotificationManager: AppNotificationManager,
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
