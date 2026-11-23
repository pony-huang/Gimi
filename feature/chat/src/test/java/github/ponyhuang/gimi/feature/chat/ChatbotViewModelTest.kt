package github.ponyhuang.gimi.feature.chat

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentExecution
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.voice.repository.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Chatbot ViewModel 的会话行为：共享 conversation 存储、ADK 落盘、失败/中断呈现与厂商约束。
 *
 * 模型上下文与落盘由 CHATBOT profile 的 ADK Runner 负责，这里以 [FakeConversationStore]
 * 模拟「事件已落盘」的读侧，ViewModel 只负责解析、投影与重试载荷。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val selection = ModelSelection("deepseek-service", "messages", "deepseek-flash")

    @Test fun `failed turn keeps one user message and retry resumes the original execution`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        var calls = 0
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            calls++
            val retry = thirdArg<Boolean>()
            if (calls == 1) {
                store.appendUser(requireNotNull(store.current()), firstArg())
                throw IllegalStateException("network down")
            }
            if (!retry) store.appendUser(requireNotNull(store.current()), firstArg())
            store.appendAssistant(requireNotNull(store.current()), "reply")
            flow { emit(replyEvent()) }
        }
        val lease = mockk<AgentRunLease>(relaxed = true)
        val vm = viewModel(store, agent(execution), lease)
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            var receipt: ChatSubmissionResult? = null
            vm.send("hello", emptyList()) { receipt = it }
            advanceUntilIdle()
            assertEquals(ChatSubmissionResult.ACCEPTED, receipt)
            assertEquals(1, vm.uiState.value.chat.messages.count { it.role == MessageRole.User })
            assertEquals(1, vm.uiState.value.messageOutcomes.values.count { it == ChatbotTurnOutcome.FAILED })
            assertNotNull(vm.uiState.value.chat.failedTurn)

            vm.onAction(ChatbotAction.Retry)
            advanceUntilIdle()

            // 重试不重发用户消息：历史里仍然只有一条 user，回复来自落盘事件。
            assertEquals(1, vm.uiState.value.chat.messages.count { it.role == MessageRole.User })
            assertEquals(ChatSubmissionResult.ACCEPTED, receipt)
            assertEquals("reply", vm.uiState.value.chat.messages.last { it.role == MessageRole.Assistant }.textParts.single().text)
            assertTrue(vm.uiState.value.messageOutcomes.isEmpty())
            coVerify(exactly = 1) { execution.send(any(), any(), true) }
            verify(exactly = 2) { lease.release() }
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `naturally finished turn leaves no running state or preparing label`() = runTest(main.dispatcher) {
        val store = FakeConversationStore().apply { emitOnRefresh = false }
        // 预先落盘完整回复：运行期间不让存储流 emit，隔离 ViewModel 收尾时最后一次 publish。
        store.create(initialModel = ModelSelectionCodec.encode(selection)).also { id ->
            store.appendUser(id, "hello")
            store.appendAssistant(id, "reply")
        }
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } returns flow { emit(replyEvent()) }
        val vm = viewModel(store, agent(execution), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            vm.send("hello", emptyList()) {}
            advanceUntilIdle()

            // 复现用户症状：回复已完整落盘，但 UI 仍停在「正在整理回复」且保留停止按钮。
            val chat = vm.uiState.value.chat
            assertEquals("reply", chat.messages.last { it.role == MessageRole.Assistant }.textParts.single().text)
            assertFalse("自然结束后 isAgentRunning 必须为 false", chat.isAgentRunning)
            assertNull("自然结束后不应再显示运行状态", chat.currentRunStatus())
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `finished turn keeps message identity across the reload`() = runTest(main.dispatcher) {
        val store = FakeConversationStore().apply { emitOnRefresh = false }
        val gate = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.RENDEZVOUS)
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            flow {
                // 流式增量与收尾完整事件同 id（ADK 同一次模型调用共享 Event.id）。
                emit(replyEvent(text = "reply").copy(partial = true, turnComplete = false))
                gate.receive()
                // 收尾落盘：用户事件 id 与乐观插入的不同、助手事件 id 与流式消息相同。
                store.appendUserMessage(requireNotNull(store.current()), Messages.fromUser("hello").copy(id = "user-event"))
                store.appendUserMessage(
                    requireNotNull(store.current()),
                    Messages.fromAssistant(id = "event-1", author = "Chatbot").copy(
                        textParts = listOf(github.ponyhuang.gimi.domain.conversation.model.TextPart("event-1:0", "reply")),
                        turnComplete = true,
                    ),
                )
                emit(replyEvent())
            }
        }
        val vm = viewModel(store, agent(execution), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            vm.send("hello", emptyList()) {}
            advanceUntilIdle()
            // 流式中途（第一个增量后挂起在 gate）：记录流式期身份。
            val streaming = vm.uiState.value.chat
            val streamingUserId = streaming.messages.last { it.role == MessageRole.User }.id
            assertEquals("流式消息直接采用真实事件 id", "event-1", streaming.messages.last { it.role == MessageRole.Assistant }.id)

            gate.send(Unit)
            advanceUntilIdle()

            // 收尾 reload 后身份保持：乐观用户消息不换 id，回答沿用事件 id，仅标志位翻为完成态。
            val chat = vm.uiState.value.chat
            assertEquals("用户消息身份跨 reload 保持", streamingUserId, chat.messages.last { it.role == MessageRole.User }.id)
            assertEquals("回答消息 id 与落盘事件同源", "event-1", chat.messages.last { it.role == MessageRole.Assistant }.id)
            assertEquals("reply", chat.messages.last { it.role == MessageRole.Assistant }.textParts.single().text)
            assertFalse(chat.messages.last { it.role == MessageRole.Assistant }.partial)
        } finally { gate.close(); holder.clear(); advanceUntilIdle() }
    }

    @Test fun `switching sessions keeps generation alive until explicit stop`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            store.appendUser(requireNotNull(store.current()), firstArg())
            flow { awaitCancellation() }
        }
        val lease = mockk<AgentRunLease>(relaxed = true)
        val vm = viewModel(store, agent(execution), lease)
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            vm.send("hello", emptyList()) {}
            advanceUntilIdle()
            val original = vm.uiState.value.sessionId
            vm.onAction(ChatbotAction.NewConversation)
            advanceUntilIdle()
            assertNotEquals(original, vm.uiState.value.sessionId)
            assertTrue(vm.uiState.value.chat.conversationTaskStatuses[original] is ConversationTaskStatus.Running)
            verify(exactly = 0) { lease.release() }
            vm.onAction(ChatbotAction.SwitchSession(original))
            advanceUntilIdle()
            vm.onAction(ChatbotAction.Stop)
            advanceUntilIdle()
            assertEquals(
                ChatbotTurnOutcome.STOPPED,
                vm.uiState.value.messageOutcomes.values.single(),
            )
            verify(exactly = 1) { lease.release() }
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `new conversation when already new emits notice and does not create new session`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val vm = viewModel(store, agent(mockk()), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            val original = vm.uiState.value.sessionId
            assertTrue(original.isNotBlank())
            assertTrue(vm.uiState.value.chat.messages.isEmpty())

            vm.onAction(ChatbotAction.NewConversation)
            advanceUntilIdle()
            assertEquals(ChatbotNotice.ALREADY_NEW_CONVERSATION, vm.uiState.value.notice)
            assertEquals(original, vm.uiState.value.sessionId)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `persisted user message without a reply reads as interrupted after restart`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        store.create(initialModel = ModelSelectionCodec.encode(selection)).also { id ->
            store.appendUser(id, "没等到回答")
        }
        val vm = viewModel(store, agent(mockk()), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            // 进程重启后：用户消息之后没有助手消息，即视为已中断。
            assertEquals(
                ChatbotTurnOutcome.INTERRUPTED,
                vm.uiState.value.messageOutcomes.values.single(),
            )
            assertNotNull(vm.uiState.value.chat.failedTurn)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `native realtime transcript without a reply keeps the utterance without a retry`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val transcript = Messages.fromUser("结束前的话").copy(isNativeRealtimeTranscript = true)
        store.create(initialModel = ModelSelectionCodec.encode(selection)).also { id ->
            store.appendUserMessage(id, transcript)
        }
        val vm = viewModel(store, agent(mockk()), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            assertEquals(listOf(transcript), vm.uiState.value.chat.messages)
            assertTrue(vm.uiState.value.messageOutcomes.isEmpty())
            assertNull(vm.uiState.value.chat.failedTurn)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `unanswered voice recording cannot resume a text execution`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        store.create(initialModel = ModelSelectionCodec.encode(selection)).also { id ->
            store.appendUser(id, "", listOf(FileAttachment(
                mimeType = "audio/wav",
                id = "recording",
                sizeBytes = 1,
                displayName = "voice.wav",
                inlineData = byteArrayOf(1),
            )))
        }
        val vm = viewModel(store, agent(execution), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            assertEquals(ChatbotTurnOutcome.VOICE_INTERRUPTED, vm.uiState.value.messageOutcomes.values.single())
            assertNull(vm.uiState.value.chat.failedTurn)
            vm.onAction(ChatbotAction.Retry)
            advanceUntilIdle()
            coVerify(exactly = 0) { execution.send(any(), any(), any()) }
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `voice-written turns appear without switching sessions`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val vm = viewModel(store, agent(mockk()), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            val id = vm.uiState.value.sessionId
            // 模拟语音侧 ChatbotConversationWriter.flush 的落盘 + 通知时序。
            store.appendUser(id, "语音里问的问题")
            store.appendAssistant(id, "语音里的回答")
            store.notifyConversationContentChanged(id)
            store.refreshConversation(id)
            advanceUntilIdle()

            // 用户症状：回到 Chatbot 页不切会话就该看到语音轮次。
            assertEquals(
                "语音里的回答",
                vm.uiState.value.chat.messages.lastOrNull { it.role == MessageRole.Assistant }?.textParts?.single()?.text,
            )
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `shared vendor files appear and can be reused without uploading again`() = runTest(main.dispatcher) {
        val entry = UploadedFileEntry("shared-file", "deepseek", "report.txt", "text/plain", 10, 1)
        val files = MutableStateFlow(listOf(entry))
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } returns flow { emit(replyEvent()) }
        val fileGateway = mockk<VendorFileGateway>(relaxed = true)
        val vm = viewModel(FakeConversationStore(), agent(execution), mockk(relaxed = true), files, fileGateway)
        val holder = ViewModelStore().apply { put("test", vm) }
        val reused = DraftAttachment(
            reference = "gimi-file://deepseek/shared-file",
            displayName = entry.displayName,
            mimeType = entry.mimeType,
            sizeBytes = entry.bytes,
            category = AttachmentCategory.DOCUMENT,
            fileId = entry.fileId,
            fileVendor = entry.vendor,
        )
        try {
            advanceUntilIdle()
            assertEquals(listOf(entry), vm.uiState.value.uploadedFiles)
            vm.saveDraft(vm.uiState.value.sessionId, MessageData(attachments = listOf(reused)))
            // 已带同厂商引用的草稿不产生上传任务：chip 直接以 isVendorUploaded 呈现就绪。
            vm.uploadDocumentDraft(reused)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.chat.draftUploadStates.isEmpty())
            coVerify(exactly = 0) { fileGateway.upload(any(), any(), any(), any(), any()) }
            vm.onAction(ChatbotAction.NewConversation)
            advanceUntilIdle()
            assertEquals(listOf(entry), vm.uiState.value.uploadedFiles)
            files.value = emptyList()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.uploadedFiles.isEmpty())
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `chip upload records the vendor file into the registry for the drawer list`() = runTest(main.dispatcher) {
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } returns flow { emit(replyEvent()) }
        val fileGateway = mockk<VendorFileGateway>(relaxed = true)
        val vm = viewModel(FakeConversationStore(), agent(execution), mockk(relaxed = true), fileGateway = fileGateway)
        val holder = ViewModelStore().apply { put("test", vm) }
        val draft = DraftAttachment(
            reference = "/tmp/draft.docx",
            displayName = "季度报表.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = 2048,
            category = AttachmentCategory.DOCUMENT,
        )
        try {
            advanceUntilIdle()
            // 与真实导入顺序一致：草稿先入会话草稿，再触发 chip 阶段预热上传。
            vm.saveDraft(vm.uiState.value.sessionId, MessageData(attachments = listOf(draft)))
            vm.uploadDocumentDraft(draft)
            advanceUntilIdle()
            assertEquals(
                DraftUploadState.Ready("uploaded-file", "deepseek"),
                vm.uiState.value.chat.draftUploadStates["/tmp/draft.docx"],
            )
            // 上传成功即进注册表（与是否发送无关）：抽屉"上传的文件"列表据此展示；
            // 缺这条记录会导致 Chatbot 上传的文件在任何界面都查不到。
            assertEquals(
                listOf(
                    UploadedFileEntry(
                        "uploaded-file", "deepseek", "季度报表.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10, 1,
                    ),
                ),
                vm.uiState.value.uploadedFiles,
            )
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `send time re-upload targets the new vendor after switching on an unlocked session`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        val sent = mutableListOf<List<FileAttachment>>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            sent.add(secondArg())
            store.appendUser(requireNotNull(store.current()), firstArg())
            store.appendAssistant(requireNotNull(store.current()), "reply")
            flow { emit(replyEvent()) }
        }
        val fileGateway = mockk<VendorFileGateway>(relaxed = true)
        val vm = viewModel(
            store, agent(execution), mockk(relaxed = true), fileGateway = fileGateway,
            instances = listOf(deepseekInstance(), anthropicInstance()),
        )
        val holder = ViewModelStore().apply { put("test", vm) }
        val draft = DraftAttachment(
            reference = "/tmp/draft.docx",
            displayName = "季度报表.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = 2048,
            category = AttachmentCategory.DOCUMENT,
        )
        try {
            advanceUntilIdle()
            vm.saveDraft(vm.uiState.value.sessionId, MessageData(attachments = listOf(draft)))
            vm.uploadDocumentDraft(draft)
            advanceUntilIdle()
            assertEquals(
                DraftUploadState.Ready("uploaded-file", "deepseek"),
                vm.uiState.value.chat.draftUploadStates["/tmp/draft.docx"],
            )
            vm.onAction(ChatbotAction.SelectModel(ModelSelection("anthropic-service", "anthropic-messages", "claude-sonnet")))
            advanceUntilIdle()
            // 发送的草稿仍带着旧厂商 file_id（与 composer 回填后的形态一致）。
            vm.send("hello", listOf(draft.copy(fileId = "uploaded-file", fileVendor = "deepseek"))) {}
            advanceUntilIdle()
            // 厂商切换后的本地副本草稿换厂商重传：deepseek chip 阶段一次 + anthropic 发送时一次。
            coVerify(exactly = 1) { fileGateway.upload("deepseek", any(), any(), any(), any()) }
            coVerify(exactly = 1) { fileGateway.upload("anthropic", any(), any(), any(), any()) }
            val sentFile = sent.single().single()
            assertEquals("uploaded-file", sentFile.fileId)
            assertEquals("anthropic", sentFile.fileVendor)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `minimax chatbot exposes its own web tool without document upload`() = runTest(main.dispatcher) {
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } returns flow { emit(replyEvent()) }
        val vm = viewModel(FakeConversationStore(), agent(execution), mockk(relaxed = true), instances = listOf(minimaxInstance()))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            // message 连接的模型默认选中；检索工具按厂商目录默认全启用
            assertEquals("minimax-text", vm.uiState.value.selection?.modelId)
            assertEquals(
                setOf(OfficialToolIds.MINIMAX_WEB_SEARCH),
                vm.uiState.value.options.enabledOfficialFunctionIds.keys,
            )
            val chat = vm.uiState.value.chat
            assertEquals(1, chat.officialToolDescriptors.size)
            assertEquals(OfficialToolIds.MINIMAX_WEB_SEARCH, chat.officialToolDescriptors.single().id)
            assertEquals("MiniMax", chat.officialToolDescriptors.single().sourceServiceName)
            // 厂商矩阵不含文件上传：文档入口随能力声明一起关闭
            val model = chat.availableLLMModelSettings.single().findModel(requireNotNull(vm.uiState.value.selection))
            assertEquals(null, model?.multimodal?.documentInput)
            assertTrue(vm.uiState.value.uploadedFiles.isEmpty())
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `openai completions are eligible but the search model requires explicit selection`() = runTest(main.dispatcher) {
        val openai = ModelServiceInstance(
            "openai-service", "OpenAI", "openai", true, true, false, "fake-key",
            listOf(ServiceConnection("openai-chat", ServiceCapability.Conversation, ApiProtocol.OpenaiChatcompletions,
                "https://api.openai.com/v1", models = listOf(
                    Model("gpt-5-search-api", "GPT-5 Search"), Model("gpt-5", "GPT-5"),
                ))),
        )
        val vm = viewModel(FakeConversationStore(), agent(mockk()), mockk(relaxed = true), instances = listOf(openai, minimaxInstance()))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            assertEquals("gpt-5", vm.uiState.value.selection?.modelId)
            val searchSelection = ModelSelection("openai-service", "openai-chat", "gpt-5-search-api")
            assertTrue(vm.uiState.value.chat.availableLLMModelSettings.any { it.templateId == "openai" })
            vm.onAction(ChatbotAction.SelectModel(searchSelection))
            advanceUntilIdle()
            assertEquals(searchSelection, vm.uiState.value.selection)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `switching vendor on an unlocked session resets the tool options`() = runTest(main.dispatcher) {
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } returns flow { emit(replyEvent()) }
        val vm = viewModel(
            FakeConversationStore(), agent(execution), mockk(relaxed = true),
            instances = listOf(deepseekInstance(), minimaxInstance()),
        )
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            val deepseekSelection = requireNotNull(vm.uiState.value.selection)
            assertEquals(
                setOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH, OfficialToolIds.DEEPSEEK_WEB_FETCH),
                vm.uiState.value.options.enabledOfficialFunctionIds.keys,
            )
            vm.onAction(ChatbotAction.SelectModel(ModelSelection("minimax-service", "minimax-messages", "minimax-text")))
            advanceUntilIdle()
            // 厂商切换后旧厂商的工具勾选无意义：重置为新厂商默认
            assertEquals(
                setOf(OfficialToolIds.MINIMAX_WEB_SEARCH),
                vm.uiState.value.options.enabledOfficialFunctionIds.keys,
            )
            assertEquals("minimax-text", vm.uiState.value.selection?.modelId)
            assertNotEquals(deepseekSelection.instanceId, vm.uiState.value.selection?.instanceId)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `locked session creates a new conversation when the model changes`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            store.appendUser(requireNotNull(store.current()), firstArg())
            store.appendAssistant(requireNotNull(store.current()), "reply")
            flow { emit(replyEvent()) }
        }
        val vm = viewModel(store, agent(execution), mockk(relaxed = true), instances = listOf(deepseekInstance(), minimaxInstance()))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            val original = vm.uiState.value.sessionId
            vm.send("hello", emptyList()) {}
            advanceUntilIdle()
            vm.onAction(ChatbotAction.SelectModel(ModelSelection("minimax-service", "minimax-messages", "minimax-text")))
            advanceUntilIdle()
            // 已提交首条消息即锁定：换模型只能新建会话，不能把旧会话的上下文改到新模型上。
            assertNotEquals(original, vm.uiState.value.sessionId)
            assertEquals("minimax-text", vm.uiState.value.selection?.modelId)
            assertTrue(vm.uiState.value.chat.messages.isEmpty())
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `documents are ignored for vendors without upload support`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        val sent = mutableListOf<List<github.ponyhuang.gimi.domain.conversation.model.FileAttachment>>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            sent.add(secondArg())
            store.appendUser(requireNotNull(store.current()), firstArg())
            store.appendAssistant(requireNotNull(store.current()), "reply")
            flow { emit(replyEvent()) }
        }
        val fileGateway = mockk<VendorFileGateway>(relaxed = true)
        val vm = viewModel(store, agent(execution), mockk(relaxed = true), fileGateway = fileGateway, instances = listOf(minimaxInstance()))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            vm.send("hello", listOf(DraftAttachment(
                reference = "file:///tmp/report.pdf",
                displayName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 10,
                category = AttachmentCategory.DOCUMENT,
            ))) { }
            advanceUntilIdle()
            // MiniMax 不在文件上传矩阵：不上传、不发送文档，轮次失败也不会发生
            coVerify(exactly = 0) { fileGateway.upload(any(), any(), any(), any(), any()) }
            assertTrue(sent.single().isEmpty())
            assertTrue(vm.uiState.value.messageOutcomes.isEmpty())
            // 展示以实际发送为准：被厂商能力挡下的文档不会进入对方看到的用户消息。
            assertTrue(
                vm.uiState.value.chat.messages.first { it.role == MessageRole.User }.fileAttachments.isEmpty(),
            )
        } finally { holder.clear(); advanceUntilIdle() }
    }

    @Test fun `drafts live in memory and are dropped after acceptance`() = runTest(main.dispatcher) {
        val store = FakeConversationStore()
        val execution = mockk<ChatAgentExecution>()
        coEvery { execution.send(any(), any(), any()) } coAnswers {
            store.appendUser(requireNotNull(store.current()), firstArg())
            store.appendAssistant(requireNotNull(store.current()), "reply")
            flow { emit(replyEvent()) }
        }
        val vm = viewModel(store, agent(execution), mockk(relaxed = true))
        val holder = ViewModelStore().apply { put("test", vm) }
        try {
            advanceUntilIdle()
            val id = vm.uiState.value.sessionId
            vm.saveDraft(id, MessageData(text = "半句话"))
            advanceUntilIdle()
            assertEquals("半句话", vm.uiState.value.draft.text)
            vm.send("半句话", emptyList()) {}
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.draft.text)
        } finally { holder.clear(); advanceUntilIdle() }
    }

    private fun agent(execution: ChatAgentExecution): ChatAgentRepository = mockk {
        coEvery { createExecution(any(), any(), any(), any(), any()) } returns execution
    }

    private fun deepseekInstance(): ModelServiceInstance = ModelServiceInstance(
        selection.instanceId, "DeepSeek", "deepseek", true, true, false, "fake-key",
        listOf(ServiceConnection(selection.connectionId, ServiceCapability.Conversation, ApiProtocol.Anthropic,
            OFFICIAL_ENDPOINT, models = listOf(Model(selection.modelId, selection.modelId)))),
    )

    private fun minimaxInstance(): ModelServiceInstance = ModelServiceInstance(
        "minimax-service", "MiniMax", "minimax", true, true, false, "fake-key",
        listOf(ServiceConnection("minimax-messages", ServiceCapability.Conversation, ApiProtocol.Anthropic,
            "https://api.minimaxi.com/anthropic", models = listOf(Model("minimax-text", "MiniMax Text")))),
    )

    private fun anthropicInstance(): ModelServiceInstance = ModelServiceInstance(
        "anthropic-service", "Anthropic", "anthropic", true, true, false, "fake-key",
        listOf(ServiceConnection("anthropic-messages", ServiceCapability.Conversation, ApiProtocol.Anthropic,
            "https://api.anthropic.com", models = listOf(Model("claude-sonnet", "Claude Sonnet")))),
    )

    private fun replyEvent(text: String = "reply") = ChatRunEvent(
        id = "event-1",
        invocationId = "invocation-1",
        author = "Chatbot",
        parts = listOf(ChatRunPart(text = text)),
        functionCalls = emptyList(),
        functionResponses = emptyList(),
        partial = false,
        turnComplete = true,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )

    private fun viewModel(
        store: FakeConversationStore,
        agent: ChatAgentRepository,
        lease: AgentRunLease,
        files: MutableStateFlow<List<UploadedFileEntry>> = MutableStateFlow(emptyList()),
        fileGateway: VendorFileGateway = mockk(relaxed = true),
        instances: List<ModelServiceInstance> = listOf(deepseekInstance()),
        attachments: github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository =
            mockk { coEvery { read(any(), emptyList()) } returns emptyList() },
    ): ChatbotViewModel {
        val catalog = mockk<ModelCatalogRepository>()
        every { catalog.observeInstances() } returns flowOf(instances)
        every { catalog.resolveConnection(any()) } answers {
            val value = firstArg<ModelSelection>()
            val instance = instances.firstOrNull { it.id == value.instanceId }
            val connection = instance?.connection(value.connectionId)
            val model = instance?.findModel(value)
            if (instance == null || connection == null || model == null) {
                null
            } else {
                ResolvedServiceConnection(
                    instanceId = instance.id,
                    templateId = instance.templateId,
                    connectionId = connection.id,
                    capability = connection.capability,
                    protocol = connection.protocol,
                    endpoint = connection.endpoint,
                    apiKey = instance.effectiveApiKey(connection),
                    workspaceId = null,
                    model = model,
                )
            }
        }
        val gate = mockk<AgentRuntimeGate>()
        coEvery { gate.acquire(AgentTaskSource.CHATBOT, any(), any()) } returns lease
        val recognition = mockk<SpeechRecognitionRepository>()
        every { recognition.availability } returns flowOf(false)
        val playback = mockk<SpeechPlaybackRepository>()
        every { playback.state } returns MutableStateFlow(SpeechPlaybackState())
        val settings = mockk<SpeechSettingsRepository>()
        every { settings.autoSpeakEnabled } returns MutableStateFlow(false)
        coEvery { attachments.read(any(), any()) } answers {
            secondArg<List<DraftAttachment>>().map { draft ->
                github.ponyhuang.gimi.domain.conversation.model.FileAttachment(
                    mimeType = draft.mimeType,
                    id = draft.reference,
                    sizeBytes = draft.sizeBytes,
                    displayName = draft.displayName,
                    payloadReference = "file:///tmp/payload",
                )
            }
        }
        val registry = mockk<UploadedFileRegistry>()
        every { registry.entriesFor(any()) } returns files
        coEvery { registry.record(any()) } coAnswers { files.value += firstArg<UploadedFileEntry>() }
        coEvery { fileGateway.upload(any(), any(), any(), any(), any()) } returns
            VendorFileUploadResult.Uploaded(fileId = "uploaded-file", bytes = 10, createdAtEpochSeconds = 1)
        return ChatbotViewModel(
            store, agent, catalog, gate, recognition, playback, settings, registry, fileGateway, attachments,
            OfficialToolSupport(),
        )
    }

    /**
     * 模拟 Chatbot 分区的 conversation 存储：会话列表、元数据与「已落盘事件」的读侧。
     * ADK Runner 的真实落盘在测试里由用例显式调用 [appendUser]/[appendAssistant] 表达。
     */
    private class FakeConversationStore : ConversationRepository {
        private val conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
        /** 关闭后 refreshConversation 不再 emit，用于隔离 ViewModel 收尾时最后一次 publish 的状态。 */
        var emitOnRefresh = true
        override val conversations = conversationsFlow
        override val conversationContentRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
        private val models = mutableMapOf<String, String>()
        private val configurations = mutableMapOf<String, ConversationToolConfiguration>()
        private val messages = mutableMapOf<String, MutableList<Message>>()
        private var privateActive: String? = null
        private var counter = 0

        fun current(): String? = privateActive

        fun create(initialModel: String = "", activate: Boolean = true): String {
            val id = "light-${++counter}"
            messages[id] = mutableListOf()
            models[id] = initialModel
            if (activate) {
                privateActive = id
                publish()
            }
            return id
        }

        fun appendUser(id: String, text: String, attachments: List<FileAttachment> = emptyList()) {
            appendUserMessage(id, Messages.fromUser(text, attachments))
        }

        fun appendUserMessage(id: String, message: Message) {
            messages.getOrPut(id) { mutableListOf() } += message
            publish()
        }

        fun appendAssistant(id: String, text: String) {
            messages.getOrPut(id) { mutableListOf() } += Messages.fromAssistant(author = "Chatbot").copy(
                textParts = listOf(github.ponyhuang.gimi.domain.conversation.model.TextPart("part", text)),
                turnComplete = true,
            )
            publish()
        }

        private fun publish() {
            conversationsFlow.value = messages.map { (id, list) ->
                Conversation(
                    id = id,
                    title = list.firstOrNull { it.role == MessageRole.User }?.textParts?.firstOrNull()?.text.orEmpty(),
                    lastMessage = list.lastOrNull { it.role == MessageRole.Assistant }?.textParts?.lastOrNull()?.text.orEmpty(),
                    model = models[id].orEmpty(),
                    isLast = id == privateActive,
                    timestamp = list.size.toLong(),
                )
            }
        }

        override suspend fun refresh() = publish()
        override suspend fun refreshConversation(sessionId: String) {
            if (emitOnRefresh) publish()
        }
        override suspend fun listConversations() = conversationsFlow.value
        override suspend fun loadMessages(sessionId: String) = messages[sessionId]?.toList()
        override suspend fun lastConversationId() = privateActive
        override suspend fun activateConversation(sessionId: String, defaultModel: String): String {
            privateActive = sessionId
            models.putIfAbsent(sessionId, defaultModel)
            publish()
            return models[sessionId].orEmpty()
        }
        override suspend fun setConversationModel(sessionId: String, model: String) {
            models[sessionId] = model
            publish()
        }
        override suspend fun conversationToolConfiguration(sessionId: String) = configurations[sessionId]
        override suspend fun setConversationToolConfiguration(
            sessionId: String,
            configuration: ConversationToolConfiguration,
        ): Boolean {
            configurations[sessionId] = configuration
            return true
        }
        override suspend fun discardConversationMetadata(sessionId: String) {
            messages.remove(sessionId)
            models.remove(sessionId)
            publish()
        }
        override suspend fun createConversation(
            initialModel: String,
            activate: Boolean,
            initialToolConfiguration: ConversationToolConfiguration?,
        ): String = create(initialModel, activate).also { id ->
            initialToolConfiguration?.let { configurations[id] = it }
        }
        override suspend fun deleteConversation(sessionId: String) {
            messages.remove(sessionId)
            models.remove(sessionId)
            configurations.remove(sessionId)
            if (privateActive == sessionId) privateActive = null
            publish()
        }
        override fun notifyConversationContentChanged(sessionId: String) {
            if (sessionId.isBlank()) return
            conversationContentRevisions.update { it + (sessionId to ((it[sessionId] ?: 0L) + 1L)) }
        }
    }

    private companion object {
        /** Chatbot 形态只支持官方 DeepSeek 端点。 */
        const val OFFICIAL_ENDPOINT: String = "https://api.deepseek.com/anthropic"
    }
}
