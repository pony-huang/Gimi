package github.ponyhuang.gimi.feature.chat.realtime

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionSnapshot
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.ObserveConversationsUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.ValidateChatAttachmentsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.DocumentInputCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.repository.ApplyVoiceConfigUseCase
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceApplyResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigDraft
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigLookup
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaults
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalPreference
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaultsRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceInputResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceMemoryRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceOverrideRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceFieldOverride
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.domain.voice.repository.orElse
import github.ponyhuang.gimi.feature.chat.ChatSubmissionResult
import github.ponyhuang.gimi.feature.chat.MessageData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 语音配置抽屉与会话的回归线：
 * 1. 链路可用性由目录实例与有效配置计算，不随通话快照挂断复位而清零；
 * 2. 半级联暂未接入具体业务：链路选择被拒绝，草稿保持原链路；
 * 3. 草稿编辑 → 应用：原子提交对话覆盖，应用后草稿回到干净状态；
 * 4. 通话中应用必须先取得重建确认，确认后按草稿配置切换或按当前有效配置重建；
 * 5. 「开始通话」先原子应用未保存草稿再启动；缺项时保留草稿并阻止启动；
 * 6. 音色快捷选择立即保存覆盖并记忆到模型；
 * 7. 新建会话仅待启动态的级联助手可用；Chatbot 预设经 mutate 闸门写穿。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeVoiceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun availabilitySurvivesControllerStateReset() = runTest {
        // 控制器快照保持默认（可用性 false），模拟挂断后整体复位；
        // 配置齐备时链路可用性必须由配置计算，不能随通话快照一起清零。
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem() // stateIn 的初始空状态
            val state = awaitItem()
            assertTrue(state.cascadedAvailable)
            assertTrue(state.realtimeAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun realtimeIsUnavailableWithoutAConfiguredRealtimeModel() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture().copy(realtimeModel = null))
        val lookup = FakeVoiceConfigLookup().apply {
            validModels += defaultsFixture().cascadedRecognitionModel!!
            validModels += defaultsFixture().cascadedSynthesisModel!!
        }
        val viewModel = viewModel(defaults = defaults, lookup = lookup)

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertTrue(state.cascadedAvailable)
            assertFalse(state.realtimeAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unkeyedRealtimeModelIsMissingFromVoiceSettingsAndCallSummary() = runTest {
        val configured = configuredSettings()
        val instance = configured.instances.single()
        val settings = configured.copy(instances = listOf(instance.copy(
            connections = instance.connections.map { connection ->
                if (connection.capability == ServiceCapability.Realtime) {
                    connection.copy(credential = CredentialSource.Independent(""))
                } else {
                    connection
                }
            },
        )))
        val defaults = FakeDefaultsRepository(defaultsFixture().copy(pipeline = VoicePipeline.NATIVE_REALTIME))
        val viewModel = viewModel(settings = settings, defaults = defaults, lookup = FakeVoiceConfigLookup())
        viewModel.prepare("conversation", ModelSelection(instance.id, "chat", "chat-model"), "当前聊天")

        viewModel.uiState.test {
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals("Chat", state.chatModelName)
            assertTrue(VoiceConfigField.REALTIME_MODEL in state.draft.missing)
            assertTrue(state.settings.realtimeRows.isEmpty())
            assertFalse(state.realtimeAvailable)
            assertEquals("", state.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun documentUploadResolvesConnectionFromCachedSettings() = runTest {
        // 回归：latestSettings 未缓存时 coordinator 的连接解析恒为 null，上传瞬间静默失败
        // （无网络请求、无日志）。设置流发射后命令式路径必须能解析到连接并走到网关。
        val settings = configuredSettings().let { base ->
            val instance = base.instances.single()
            base.copy(
                instances = listOf(instance.copy(connections = instance.connections.map { connection ->
                    if (connection.id == "chat") {
                        connection.copy(models = listOf(Model(
                            id = "chat-model",
                            name = "Chat",
                            multimodal = MultimodalCapabilities(
                                documentInput = DocumentInputCapability(ingestMode = FileIngestMode.FILE_REFERENCE),
                            ),
                        )))
                    } else {
                        connection
                    }
                })),
                assistantSelection = ModelSelection(instance.id, "chat", "chat-model"),
            )
        }
        val fileGateway = mockk<github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway>(relaxed = true)
        coEvery { fileGateway.upload(any(), any(), any(), any(), any()) } returns
            github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult.Uploaded("file-1", 10, 1)
        val uploadedFileRegistry = mockk<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry>(relaxed = true)
        val viewModel = viewModel(settings = settings, fileGateway = fileGateway, uploadedFileRegistry = uploadedFileRegistry)
        advanceUntilIdle()

        viewModel.uploadDocument(DraftAttachment(
            reference = "/tmp/report.pdf",
            displayName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 10,
            category = AttachmentCategory.DOCUMENT,
        ))
        advanceUntilIdle()

        coVerify(exactly = 1) { fileGateway.upload("qwen", any(), any(), any(), any()) }
        coVerify(exactly = 1) { uploadedFileRegistry.record(any()) }
    }

    @Test
    fun mimoOmniEnablesSemiCascadedWithOnlyGlobalSynthesisConfigured() = runTest {
        val settings = configuredSettings()
        val instance = settings.instances.single()
        val instances = listOf(instance.copy(connections = instance.connections.map { connection ->
            if (connection.id == "chat") {
                connection.copy(models = listOf(Model("mimo-v2.6-pro", "MiMo 2.6 Pro")))
            } else {
                connection
            }
        }))
        val viewModel = viewModel(settings = settings.copy(instances = instances))
        val selection = ModelSelection(instance.id, "chat", "mimo-v2.6-pro")
        viewModel.prepare("conversation", selection, "")

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.chatModelName != "MiMo 2.6 Pro") state = awaitItem()
            assertTrue(state.semiCascadedAvailable)
            assertEquals(defaultsFixture().cascadedSynthesisModel, state.draft.semiSynthesisModel.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun completePipelineSelectionSavesAutomatically() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture())
        val overrides = FakeOverridesRepository()
        val lookup = FakeVoiceConfigLookup().apply { validModels += defaultsFixture().realtimeModel!! }
        val viewModel = viewModel(defaults = defaults, overrides = overrides, lookup = lookup)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")

        viewModel.prepare("conversation", selection, "当前聊天")
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectPipeline(VoicePipeline.NATIVE_REALTIME)
            runCurrent()
            val state = expectMostRecentItem()
            assertFalse(state.draft.isDirty)
            assertEquals("Qwen Realtime", state.modelName)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VoicePipeline.NATIVE_REALTIME, overrides.storage["conversation"]?.pipeline?.orElse(null))
    }

    @Test
    fun selectingVoiceDuringCallLeavesSavedConfigurationUntouched() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState(active = true))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) { every { state } returns call }
        val overrides = FakeOverridesRepository()
        val viewModel = viewModel(controller = controller, overrides = overrides)
        viewModel.prepare("conversation", null, "当前聊天")

        viewModel.openVoiceSettings()
        viewModel.selectPipelineVoice(VoicePipeline.CASCADED, "voice-1")
        runCurrent()

        assertTrue(overrides.storage.isEmpty())
    }

    @Test
    fun draftFieldModelChangeRestoresInheritWhenCleared() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture())
        val overrides = FakeOverridesRepository()
        val viewModel = viewModel(defaults = defaults, overrides = overrides)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")

        viewModel.prepare("conversation", selection, "当前聊天")
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectFieldModel(VoiceConfigField.CASCADED_SYNTHESIS_MODEL, ModelSelection("i", "c", "m"))
            var draft = awaitItem().draft
            while (draft.cascadedSynthesisModel.value != ModelSelection("i", "c", "m")) {
                draft = awaitItem().draft
            }
            assertTrue(draft.cascadedSynthesisModel.overridden)
            // null 选择表示恢复继承：覆盖移除，草稿相对已提交内容不再算覆盖。
            viewModel.selectFieldModel(VoiceConfigField.CASCADED_SYNTHESIS_MODEL, null)
            var restored = awaitItem().draft
            // 覆盖状态与解析值分属两路流，重算有先后：两者同时到位才算恢复完成。
            while (restored.cascadedSynthesisModel.overridden ||
                restored.cascadedSynthesisModel.value != defaultsFixture().cascadedSynthesisModel
            ) {
                restored = awaitItem().draft
            }
            assertFalse(restored.cascadedSynthesisModel.overridden)
            assertEquals(defaultsFixture().cascadedSynthesisModel, restored.cascadedSynthesisModel.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun switchingSynthesisModelClearsDraftVoiceOverride() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture())
        val overrides = FakeOverridesRepository()
        val viewModel = viewModel(defaults = defaults, overrides = overrides)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")
        viewModel.prepare("conversation", selection, "当前聊天")

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectPipelineVoice(VoicePipeline.CASCADED, "voice-1")
            var draft = awaitItem().draft
            while (draft.cascadedVoice.value != "voice-1") {
                draft = awaitItem().draft
            }
            assertTrue(draft.cascadedVoice.overridden)

            // 换合成模型：旧音色与新模型不绑定，覆盖同步清空回到继承（厂商默认音色）。
            viewModel.selectFieldModel(VoiceConfigField.CASCADED_SYNTHESIS_MODEL, ModelSelection("i", "c", "m"))
            var switched = awaitItem().draft
            while (switched.cascadedSynthesisModel.value != ModelSelection("i", "c", "m") ||
                switched.cascadedVoice.overridden
            ) {
                switched = awaitItem().draft
            }
            assertFalse(switched.cascadedVoice.overridden)
            assertNull(switched.cascadedVoice.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repickingSameSynthesisModelKeepsDraftVoiceOverride() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture())
        val overrides = FakeOverridesRepository()
        val viewModel = viewModel(defaults = defaults, overrides = overrides)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")
        viewModel.prepare("conversation", selection, "当前聊天")

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectPipelineVoice(VoicePipeline.CASCADED, "voice-1")
            var draft = awaitItem().draft
            while (draft.cascadedVoice.value != "voice-1") {
                draft = awaitItem().draft
            }
            // 重选当前有效合成模型不算变化：音色覆盖保留，不被误清。
            viewModel.selectFieldModel(VoiceConfigField.CASCADED_SYNTHESIS_MODEL, defaultsFixture().cascadedSynthesisModel)
            var latest = awaitItem().draft
            while (latest.cascadedSynthesisModel.value != defaultsFixture().cascadedSynthesisModel) {
                latest = awaitItem().draft
            }
            assertTrue(latest.cascadedVoice.overridden)
            assertEquals("voice-1", latest.cascadedVoice.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun callCannotStartUntilAutomaticSaveFinishes() = runTest {
        val saveGate = CompletableDeferred<Unit>()
        val overrides = FakeOverridesRepository().apply { beforeApply = { saveGate.await() } }
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(controller = controller, overrides = overrides)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")
        viewModel.prepare("conversation", selection, "当前聊天")

        viewModel.openVoiceSettings()
        viewModel.selectPipeline(VoicePipeline.NATIVE_REALTIME)
        viewModel.closeVoiceSettings()
        runCurrent()
        viewModel.startCall()
        verify(exactly = 0) { controller.start(any(), any(), any(), any(), any()) }

        saveGate.complete(Unit)
        runCurrent()
        viewModel.startCall()
        verify(exactly = 1) {
            controller.start("conversation", selection, "当前聊天", VoiceConversationStyle.ASSISTANT, VoicePipeline.NATIVE_REALTIME)
        }
    }

    @Test
    fun closingIncompletePipelineKeepsPreviousConfigurationForNextCall() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture().copy(realtimeModel = null))
        val overrides = FakeOverridesRepository()
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(controller = controller, defaults = defaults, overrides = overrides)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")
        viewModel.prepare("conversation", selection, "当前聊天")

        viewModel.uiState.test {
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectPipeline(VoicePipeline.NATIVE_REALTIME)
            runCurrent()
            assertTrue(VoiceConfigField.REALTIME_MODEL in expectMostRecentItem().draft.missing)
            assertTrue(overrides.storage.isEmpty())

            viewModel.closeVoiceSettings()
            viewModel.startCall()
            verify(exactly = 1) {
                controller.start("conversation", selection, "当前聊天", VoiceConversationStyle.ASSISTANT, VoicePipeline.CASCADED)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun fillingLastMissingModelSavesTheEntirePipeline() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture().copy(realtimeModel = null))
        val overrides = FakeOverridesRepository()
        val viewModel = viewModel(defaults = defaults, overrides = overrides)
        val realtimeModel = defaultsFixture().realtimeModel
        viewModel.prepare("conversation", null, "当前聊天")
        viewModel.openVoiceSettings()
        viewModel.selectPipeline(VoicePipeline.NATIVE_REALTIME)
        runCurrent()
        assertTrue(overrides.storage.isEmpty())

        viewModel.selectFieldModel(VoiceConfigField.REALTIME_MODEL, realtimeModel)
        viewModel.closeVoiceSettings()
        runCurrent()

        val saved = overrides.storage.getValue("conversation")
        assertEquals(VoicePipeline.NATIVE_REALTIME, saved.pipeline.orElse(null))
        assertEquals(realtimeModel, saved.realtimeModel.orElse(null))
    }

    @Test
    fun voiceSelectionOnlyChangesTheCurrentConversation() = runTest {
        val overrides = FakeOverridesRepository()
        val memory = FakeVoiceMemoryRepository()
        val synthesisModel = defaultsFixture().cascadedSynthesisModel!!
        val lookup = FakeVoiceConfigLookup(memory).apply {
            validModels += synthesisModel
            validModels += defaultsFixture().cascadedRecognitionModel!!
        }
        val viewModel = viewModel(overrides = overrides, lookup = lookup)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")

        viewModel.prepare("conversation", selection, "当前聊天")
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.openVoiceSettings()
            viewModel.selectPipelineVoice(VoicePipeline.CASCADED, "voice-1")
            viewModel.closeVoiceSettings()
            runCurrent()
            assertEquals("voice-1", overrides.current("conversation").cascadedVoice.orElse(null))

            viewModel.prepare("other", selection, "另一个聊天")
            runCurrent()
            assertNull(expectMostRecentItem().draft.cascadedVoice.value)
            assertFalse(overrides.current("other").hasAnyOverride)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(memory.remembered.isEmpty())
    }

    @Test
    fun voiceSelectionReportsFailureWithoutPersistingTheNewVoice() = runTest {
        val overrides = FakeOverridesRepository().apply { failApply = true }
        val viewModel = viewModel(overrides = overrides)

        viewModel.prepare("conversation", ModelSelection("instance-qwen", "chat", "chat-model"), "当前聊天")
        viewModel.openVoiceSettings()
        viewModel.selectPipelineVoice(VoicePipeline.CASCADED, "voice-1")
        advanceUntilIdle()

        assertTrue(overrides.storage.isEmpty())
        assertEquals(
            RealtimeVoiceViewModel.ConfigNotice.SaveFailed(setOf(VoiceConfigField.CASCADED_VOICE)),
            viewModel.configNotices.value.second,
        )
    }

    @Test
    fun startCallValidatesCommittedConfigEvenWithoutAnOpenDraft() = runTest {
        val defaults = FakeDefaultsRepository(defaultsFixture().copy(cascadedSynthesisModel = null))
        val lookup = FakeVoiceConfigLookup().apply {
            validModels += defaultsFixture().cascadedRecognitionModel!!
        }
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(controller = controller, defaults = defaults, lookup = lookup)

        viewModel.prepare("conversation", ModelSelection("instance-qwen", "chat", "chat-model"), "当前聊天")
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.startCall()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        verify(exactly = 0) { controller.start(any(), any(), any(), any(), any()) }
        assertEquals(
            RealtimeVoiceViewModel.ConfigNotice.Problem.MissingRequired,
            viewModel.configNotices.value.second,
        )
    }

    @Test
    fun editingPausesInputWithoutChangingUserMuteAndClosingRetainsDraft() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState(active = true, style = VoiceConversationStyle.ASSISTANT))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) { every { state } returns call }
        val vm = viewModel(controller = controller)
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.editInput()
            assertTrue(awaitItem().composer.editing)
            vm.updateDraft(MessageData("保留的草稿"))
            assertEquals("保留的草稿", awaitItem().composer.draft.text)
            vm.closeInput()
            val closed = awaitItem().composer
            assertFalse(closed.editing)
            assertEquals("保留的草稿", closed.draft.text)
            verify { controller.setInputPaused(true); controller.setInputPaused(false) }
            verify(exactly = 0) { controller.toggleMute() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inputBeforeCallShowsStartHintAndDoesNotPauseMicrophone() = runTest {
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val vm = viewModel(controller = controller)
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.editInput()
            val composer = awaitItem().composer
            assertFalse(composer.editing)
            assertEquals(VoiceComposerError.StartCall, composer.error)
            verify(exactly = 0) { controller.setInputPaused(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun microphoneBeforeCallShowsToastNoticeAndDuringCallTogglesMute() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState())
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) { every { state } returns call }
        val vm = viewModel(controller = controller)
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.toggleMute()
            val notice = awaitItem().composer
            assertEquals(VoiceComposerError.StartCall, notice.error)
            verify(exactly = 0) { controller.toggleMute() }
            vm.consumeInputNotice(notice.noticeRevision)
            awaitItem()
            call.value = RealtimeVoiceCallState(active = true)
            awaitItem()
            vm.toggleMute()
            verify(exactly = 1) { controller.toggleMute() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedSubmissionKeepsDraftAndSuccessfulRetryClosesEditor() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState(active = true, callId = 5))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns call
            coEvery { submit(any()) } returnsMany listOf(
                VoiceInputResult.Failed,
                VoiceInputResult.Accepted,
            )
        }
        val vm = viewModel(controller = controller)
        vm.prepare("conversation", ModelSelection("instance-qwen", "chat", "model"), "")
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.editInput()
            awaitItem()
            val draft = MessageData("手动输入")
            vm.updateDraft(draft)
            awaitItem()
            var result: ChatSubmissionResult? = null
            vm.submitInput(draft) { result = it }
            runCurrent()
            val failed = expectMostRecentItem().composer
            assertEquals(ChatSubmissionResult.REJECTED, result)
            assertEquals(draft, failed.draft)
            assertTrue(failed.editing)
            vm.submitInput(draft) { result = it }
            runCurrent()
            val accepted = expectMostRecentItem().composer
            assertEquals(ChatSubmissionResult.ACCEPTED, result)
            assertEquals("", accepted.draft.text)
            assertFalse(accepted.editing)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun navigationWaitsUntilCallCleanupFinishes() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState(active = true))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns call
            every { end() } answers { call.value = call.value.copy(active = false, ending = true) }
        }
        val viewModel = viewModel(controller = controller)
        var navigated = false
        viewModel.stopForNavigation { navigated = true }
        runCurrent()
        assertFalse(navigated)
        call.value = RealtimeVoiceCallState()
        runCurrent()
        assertTrue(navigated)
        verify(exactly = 1) { controller.end() }
    }

    @Test
    fun chatbotModeIsTakenFromPreparedPageWhenStartingVoice() = runTest {
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(controller = controller)
        val selection = ModelSelection("instance-qwen", "chat", "chat-model")
        viewModel.prepare("chatbot-session", selection, "Chatbot", VoiceConversationStyle.CHATBOT)
        viewModel.startCall()
        runCurrent()
        verify { controller.start("chatbot-session", selection, "Chatbot", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED) }
    }


    @Test
    fun navigationClearsDraftWhileKeepingCurrentModelCapabilities() = runTest {
        val vm = viewModel()
        vm.prepare("conversation", ModelSelection("instance-qwen", "chat", "chat-model"), "当前会话")
        vm.uiState.test {
            awaitItem()
            runCurrent()
            val previous = vm.uiState.value.composer
            assertEquals("qwen", previous.vendorTemplateId)
            vm.updateDraft(MessageData("旧会话的输入草稿"))
            vm.stopForNavigation { }
            runCurrent()
            val current = vm.uiState.value.composer
            assertEquals("", current.draft.text)
            assertEquals(previous.vendorTemplateId, current.vendorTemplateId)
            assertEquals(previous.capabilities, current.capabilities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatedNavigationKeepsOnlyLatestPendingDestination() = runTest {
        val call = MutableStateFlow(RealtimeVoiceCallState(ending = true))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) { every { state } returns call }
        val vm = viewModel(controller = controller)
        val destinations = mutableListOf<String>()
        vm.stopForNavigation { destinations += "first" }
        runCurrent()
        vm.stopForNavigation { destinations += "second" }
        runCurrent()
        call.value = RealtimeVoiceCallState()
        runCurrent()
        assertEquals(listOf("second"), destinations)
    }


    @Test
    fun missingAssistantBindingDoesNotFallBackToChatbot() = runTest {
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val vm = viewModel(controller = controller)
        vm.prepare("assistant-session", null, "助手", VoiceConversationStyle.ASSISTANT)
        vm.startCall()
        runCurrent()
        verify(exactly = 0) { controller.start(any(), any(), any(), any(), any()) }
        assertEquals(RealtimeVoiceViewModel.ConfigNotice.Problem.NoConversation, vm.configNotices.value.second)
    }

    private fun viewModel(
        settings: DefaultModelSettings = configuredSettings(),
        controller: RealtimeVoiceCallController = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        },
        defaults: VoiceGlobalDefaultsRepository = FakeDefaultsRepository(defaultsFixture()),
        overrides: VoiceOverrideRepository = FakeOverridesRepository(),
        lookup: VoiceConfigLookup = FakeVoiceConfigLookup().apply {
            // 默认登记全局默认的全部模型；缺项用例自行构造并只登记应有效的部分。
            validModels += defaultsFixture().cascadedRecognitionModel!!
            validModels += defaultsFixture().cascadedSynthesisModel!!
            validModels += defaultsFixture().realtimeModel!!
        },
        fileGateway: github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway = mockk(relaxed = true),
        uploadedFileRegistry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry = mockk(relaxed = true),
    ): RealtimeVoiceViewModel {
        val voiceCapabilities = mockk<VoiceCapabilityResolver>(relaxed = true) {
            every { isSupported(any(), ModelCapability.Realtime) } returns true
        }
        val observeSettings = mockk<ObserveDefaultModelSettingsUseCase>()
        every { observeSettings() } returns MutableStateFlow(settings)
        return RealtimeVoiceViewModel(
            controller = controller,
            runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
            ttsVoiceRepository = mockk<TtsVoiceRepository>(relaxed = true),
            voiceCapabilities = voiceCapabilities,
            voiceCatalog = mockk(relaxed = true),
            defaultsRepository = defaults,
            overridesRepository = overrides,
            applyVoiceConfig = ApplyVoiceConfigUseCase(defaults, overrides, lookup),
            lookup = lookup,
            observeSettings = observeSettings,
            attachmentRepository = mockk(relaxed = true),
            fileGateway = fileGateway,
            uploadedFileRegistry = uploadedFileRegistry,
            validateAttachments = ValidateChatAttachmentsUseCase(),
        )
    }

    /** 全局默认 fixture：三条链路的默认模型齐备且在 FakeVoiceConfigLookup 中登记为有效。 */
    private fun defaultsFixture(): VoiceGlobalDefaults = VoiceGlobalDefaults(
        pipeline = VoicePipeline.CASCADED,
        cascadedRecognitionModel = ModelSelection("instance-qwen", "conn-speech", "speech-model"),
        cascadedSynthesisModel = ModelSelection("instance-qwen", "conn-tts", "tts-model"),
        realtimeModel = ModelSelection("instance-qwen", "conn-realtime", "qwen-realtime"),
    )

    /** 候选行 fixture：与 [defaultsFixture] 的模型选择对应，供设置抽屉展示。 */
    private fun configuredSettings(): DefaultModelSettings = DefaultModelSettings(
        instances = listOf(
            ModelServiceInstance(
                id = "instance-qwen",
                name = "Qwen",
                templateId = "qwen",
                isEnabled = true,
                isOfficialToolsEnabled = false,
                sharedApiKey = "key",
                connections = listOf(
                    ServiceConnection(
                        id = "conn-realtime",
                        capability = ServiceCapability.Realtime,
                        protocol = ApiProtocol.OpenaiChatcompletions,
                        endpoint = "wss://example.com",
                        models = listOf(
                            Model(
                                id = "qwen-realtime",
                                name = "Qwen Realtime",
                                capabilities = setOf(ModelCapability.Realtime),
                            ),
                        ),
                    ),
                    ServiceConnection(
                        id = "chat",
                        capability = ServiceCapability.Conversation,
                        protocol = ApiProtocol.OpenaiChatcompletions,
                        endpoint = "https://example.com",
                        models = listOf(Model("chat-model", "Chat")),
                    ),
                ),
            ),
        ),
        assistantSelection = null,
        fastSelection = null,
        speechSelection = null,
        ttsSelection = null,
        ttsVoiceId = "",
        realtimeSelection = ModelSelection("instance-qwen", "conn-realtime", "qwen-realtime"),
    )
}

/** 内存版全局默认仓库。 */
private class FakeDefaultsRepository(
    initial: VoiceGlobalDefaults,
) : VoiceGlobalDefaultsRepository {
    override val defaults = MutableStateFlow(initial)

    override suspend fun update(transform: (VoiceGlobalPreference) -> VoiceGlobalPreference) {
            val current = defaults.value
            val updated = transform(
                VoiceGlobalPreference(
                    pipeline = current.pipeline,
                    semiCascadedUnderstandingModel = current.semiCascadedUnderstandingModel,
                    semiCascadedSynthesisModel = current.semiCascadedSynthesisModel,
                ),
            )
            defaults.value = current.copy(
                pipeline = updated.pipeline,
                semiCascadedUnderstandingModel = updated.semiCascadedUnderstandingModel,
                semiCascadedSynthesisModel = updated.semiCascadedSynthesisModel,
            )
        }
}

/** 内存版对话覆盖仓库；apply 的幂等语义与真实仓库一致。 */
private class FakeOverridesRepository : VoiceOverrideRepository {
    val storage = mutableMapOf<String, ConversationVoiceOverrides>()
    private val flows = mutableMapOf<String, MutableStateFlow<ConversationVoiceOverrides>>()
    /** 注入落盘失败，验证界面不会误显示已保存。 */
    var failApply = false
    var beforeApply: suspend () -> Unit = {}

    override val rebuildCount = MutableStateFlow(0)
    override suspend fun acknowledgeRebuilds() = Unit

    private fun mutableFlow(conversationId: String): MutableStateFlow<ConversationVoiceOverrides> =
        flows.getOrPut(conversationId) {
            MutableStateFlow(storage[conversationId] ?: ConversationVoiceOverrides(conversationId))
        }

    override fun observe(conversationId: String): StateFlow<ConversationVoiceOverrides> =
        mutableFlow(conversationId)

    override fun current(conversationId: String): ConversationVoiceOverrides =
        storage[conversationId] ?: ConversationVoiceOverrides(conversationId)

    override suspend fun apply(draft: VoiceConfigDraft): VoiceApplyResult {
        beforeApply()
        if (failApply) throw java.io.IOException("disk full")
        val id = draft.overrides.conversationId
        if (storage[id] == draft.overrides) return VoiceApplyResult.Unchanged
        storage[id] = draft.overrides
        mutableFlow(id).value = draft.overrides
        return VoiceApplyResult.Applied
    }

    override suspend fun resetFields(conversationId: String, fields: Set<VoiceConfigField>) {
        val current = storage[conversationId] ?: return
        val cleared = current.copy(
            pipeline = if (VoiceConfigField.PIPELINE in fields) VoiceFieldOverride.Inherit else current.pipeline,
            cascadedRecognitionModel = if (VoiceConfigField.CASCADED_RECOGNITION_MODEL in fields) {
                VoiceFieldOverride.Inherit
            } else {
                current.cascadedRecognitionModel
            },
            cascadedSynthesisModel = if (VoiceConfigField.CASCADED_SYNTHESIS_MODEL in fields) {
                VoiceFieldOverride.Inherit
            } else {
                current.cascadedSynthesisModel
            },
            cascadedVoice = if (VoiceConfigField.CASCADED_VOICE in fields) VoiceFieldOverride.Inherit else current.cascadedVoice,
            semiCascadedUnderstandingModel = if (VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL in fields) {
                VoiceFieldOverride.Inherit
            } else {
                current.semiCascadedUnderstandingModel
            },
            semiCascadedSynthesisModel = if (VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL in fields) {
                VoiceFieldOverride.Inherit
            } else {
                current.semiCascadedSynthesisModel
            },
            semiCascadedVoice = if (VoiceConfigField.SEMI_CASCADED_VOICE in fields) {
                VoiceFieldOverride.Inherit
            } else {
                current.semiCascadedVoice
            },
            realtimeModel = if (VoiceConfigField.REALTIME_MODEL in fields) VoiceFieldOverride.Inherit else current.realtimeModel,
            realtimeVoice = if (VoiceConfigField.REALTIME_VOICE in fields) VoiceFieldOverride.Inherit else current.realtimeVoice,
        )
        storage[conversationId] = cleared
        mutableFlow(conversationId).value = cleared
    }

    override suspend fun clear(conversationId: String) {
        storage.remove(conversationId)
        flows.remove(conversationId)
    }
}

/** 内存版模型音色记忆仓库。 */
private class FakeVoiceMemoryRepository : VoiceMemoryRepository {
    val remembered = mutableListOf<Triple<VoicePipeline, ModelSelection, String>>()
    private val store = mutableMapOf<Pair<VoicePipeline, ModelSelection>, String>()

    override suspend fun rememberVoice(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String) {
        remembered += Triple(pipeline, selection, voiceId)
        store[pipeline to selection] = voiceId
    }

    override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
        store[pipeline to selection]
}

/** 内存版配置查询：只有显式登记的模型选择有效。 */
private class FakeVoiceConfigLookup(private val memory: VoiceMemoryRepository? = null) : VoiceConfigLookup {
    val validModels = mutableSetOf<ModelSelection>()

    override fun isModelValid(selection: ModelSelection): Boolean = selection in validModels

    override fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean? = null

    override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
        memory?.rememberedVoice(pipeline, selection)
}
