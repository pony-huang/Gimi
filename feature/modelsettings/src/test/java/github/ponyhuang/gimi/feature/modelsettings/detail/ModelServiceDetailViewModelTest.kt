package github.ponyhuang.gimi.feature.modelsettings.detail

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeletionImpactUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeleteModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveNewModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelInstanceEnabledUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 服务详情编辑器的行为契约：草稿与启停分离、模型确认写回、保存校验与建议流程。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadingExistingInstanceBuildsDraftFromSavedConnections() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isNewInstance)
        assertEquals("OpenAI 个人", state.draft?.name)
        assertEquals(
            listOf(ServiceCapability.Conversation, ServiceCapability.Recognition, ServiceCapability.Synthesis, ServiceCapability.Realtime),
            state.draft?.connections?.map { it.capability },
        )
        assertNull(state.baseline?.let { draft -> if (draft == state.draft) null else draft })
        assertFalse(state.isDirty)
        // 已有实例显示启停状态
        assertTrue(state.isEnabled)
    }

    @Test
    fun externalEnableChangesSyncWithoutClobberingDirtyDrafts() = runTest {
        val instancesFlow = MutableStateFlow(listOf(openAiInstance()))
        val repository = repository(instances = emptyList(), instancesFlow = instancesFlow)
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        // 用户开始编辑但未保存
        viewModel.onAction(ModelServiceDetailAction.NameChanged("改过的名字"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDirty)

        // 外部停用：启停显示状态跟随仓库，草稿不被覆盖
        instancesFlow.value = listOf(openAiInstance().copy(isEnabled = false))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEnabled)
        assertEquals("改过的名字", state.draft?.name)
        assertTrue(state.isDirty)
    }

    @Test
    fun confirmingModelSelectionWritesWorkingSetBackIntoDraft() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        val conversationKey = viewModel.uiState.value.candidates
            .first { it.capability == ServiceCapability.Conversation }.key
        viewModel.onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Models))
        viewModel.onAction(ModelServiceDetailAction.ToggleModel(conversationKey))
        viewModel.onAction(ModelServiceDetailAction.ConfirmModels)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ServiceEditorPage.Detail, state.page)
        assertEquals(
            emptyList<String>(),
            state.draft?.conversationConnection?.models?.map { it.id },
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun savingExistingInstanceRebasesBaselineSoBackDoesNotPromptDiscard() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        // 假仓库的保存像真实存储一样把草稿写回实例流（归一化后的已保存状态）。
        val flow = repository.observeInstances() as MutableStateFlow
        coEvery { repository.saveInstance(any()) } answers {
            val draft = firstArg<ModelServiceDraft>()
            flow.value = flow.value.map { instance ->
                if (instance.id == draft.instanceId) {
                    instance.copy(
                        name = draft.name.trim(),
                        sharedApiKey = draft.sharedApiKey.trim(),
                        connections = draft.connections,
                    )
                } else {
                    instance
                }
            }
            true
        }
        val viewModel = createViewModel(repository)
        // 默认分配已配置：保存直达，不进用途建议页。
        val configuredSelection = ModelSelection("instance-openai", "conn-conversation", "gpt-4.1")
        every { repository.currentAssistantSelection() } returns configuredSelection
        every { repository.currentFastSelection() } returns configuredSelection
        every { repository.currentSpeechSelection() } returns configuredSelection
        every { repository.currentTtsSelection() } returns configuredSelection
        every { repository.currentRealtimeSelection() } returns configuredSelection
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        viewModel.onAction(ModelServiceDetailAction.NameChanged("改过的名字"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.onAction(ModelServiceDetailAction.Save)
        advanceUntilIdle()

        // 保存成功后就地收编：草稿与基线一致，返回不再误报「放弃未保存的修改」。
        val state = viewModel.uiState.value
        assertFalse(state.isDirty)
        assertEquals(state.baseline, state.draft)
        assertEquals("改过的名字", state.draft?.name)
    }

    @Test
    fun saveRequiresAtLeastOneSelectedModel() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        // 清空全部已选模型后保存：被校验拒绝，不触发仓库写入
        val allKeys = viewModel.uiState.value.candidates.map { it.key }
        viewModel.onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Models))
        allKeys.forEach { key -> viewModel.onAction(ModelServiceDetailAction.ToggleModel(key)) }
        viewModel.onAction(ModelServiceDetailAction.ConfirmModels)
        viewModel.onAction(ModelServiceDetailAction.Save)
        advanceUntilIdle()

        assertEquals(R_error, viewModel.uiState.value.errorRes)
        coVerify(exactly = 0) { repository.saveInstance(any()) }
    }

    @Test
    fun savingNewInstanceFromRoleOriginSkipsSuggestions() = runTest {
        val repository = repository(instances = emptyList())
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load(null, "openai", DefaultModelRole.SpeechRecognition))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isNewInstance)
        assertEquals("OpenAI", state.draft?.name)
        viewModel.onAction(ModelServiceDetailAction.SharedApiKeyChanged("sk-key"))
        // 选择对话模型，使保存通过结构校验
        val conversationKey = viewModel.uiState.value.candidates
            .first { it.capability == ServiceCapability.Conversation }.key
        viewModel.onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Models))
        viewModel.onAction(ModelServiceDetailAction.ToggleModel(conversationKey))
        viewModel.onAction(ModelServiceDetailAction.ConfirmModels)
        // 用途来源流程：直接保存，不进入建议确认页
        viewModel.onAction(ModelServiceDetailAction.Save)
        advanceUntilIdle()

        assertEquals(ServiceEditorPage.Detail, viewModel.uiState.value.page)
        coVerify(exactly = 1) { repository.saveNewInstance(any()) }
        // 用途分配由宿主回到选择器后由用户确认一次，保存流程不代写
        coVerify(exactly = 0) { repository.selectSpeechModel(any()) }
    }

    @Test
    fun ordinaryAddShowsEmptyRoleSuggestionsBeforeSaving() = runTest {
        val repository = repository(instances = emptyList(), allDefaultsEmpty = true)
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load(null, "openai", null))
        advanceUntilIdle()

        viewModel.onAction(ModelServiceDetailAction.SharedApiKeyChanged("sk-key"))
        // 选择一个识别模型，让识别角色出现建议
        val recognitionKey = viewModel.uiState.value.candidates
            .first { it.capability == ServiceCapability.Recognition }.key
        viewModel.onAction(ModelServiceDetailAction.OpenPage(ServiceEditorPage.Models))
        viewModel.onAction(ModelServiceDetailAction.ToggleModel(recognitionKey))
        viewModel.onAction(ModelServiceDetailAction.ConfirmModels)
        viewModel.onAction(ModelServiceDetailAction.Save)
        advanceUntilIdle()

        // 建议确认页展示空用途建议：对话模型同时建议助手与快速角色
        val state = viewModel.uiState.value
        assertEquals(ServiceEditorPage.Suggestions, state.page)
        assertEquals(
            setOf(DefaultModelRole.Assistant, DefaultModelRole.Fast),
            state.suggestions.orEmpty().map { it.role }.toSet(),
        )

        // 确认后保存服务并把建议写入默认分配
        coEvery { repository.saveNewInstance(any()) } answers {
            val draft = firstArg<ModelServiceDraft>()
            ModelServiceInstance(
                id = "instance-new", name = draft.name, templateId = draft.templateId,
                isEnabled = true, isOfficialToolsEnabled = draft.isOfficialToolsEnabled,
                sharedApiKey = draft.sharedApiKey, connections = draft.connections,
            )
        }
        // SharedFlow(replay=0)：先订阅再触发，保证保存事件不丢
        val collected = mutableListOf<ModelServiceDetailEffect>()
        val collector = launch { viewModel.effects.collect { collected += it } }
        viewModel.onAction(ModelServiceDetailAction.ConfirmSuggestions)
        advanceUntilIdle()
        collector.cancelAndJoin()

        coVerify(exactly = 1) { repository.saveNewInstance(any()) }
        coVerify(exactly = 1) { repository.selectAssistantModel(any()) }
        val saved = collected.filterIsInstance<ModelServiceDetailEffect.Saved>().single()
        assertEquals("instance-new", saved.instanceId)
        assertTrue(saved.popToServiceList)
    }

    @Test
    fun draftSaveDoesNotCarryEnabledState() = runTest {
        val repository = repository(instances = listOf(openAiInstance().copy(isEnabled = false)))
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEnabled)

        viewModel.onAction(ModelServiceDetailAction.NameChanged("编辑停用的服务"))
        // 普通编辑保存先进建议确认页（fixture 的默认引用为空）
        viewModel.onAction(ModelServiceDetailAction.Save)
        advanceUntilIdle()
        assertEquals(ServiceEditorPage.Suggestions, viewModel.uiState.value.page)
        viewModel.onAction(ModelServiceDetailAction.ConfirmSuggestions)
        advanceUntilIdle()

        // 草稿保存不携带启停状态：仓库读取存储中的最新值（false 保持不变）
        coVerify(exactly = 1) { repository.saveInstance(any()) }
        // 草稿类型本身不含 isEnabled 字段；启停通过独立入口提交
        coVerify(exactly = 0) { repository.setInstanceEnabled(any(), any()) }
    }

    @Test
    fun independentEnableCommitDoesNotWaitForDraftSave() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        viewModel.onAction(ModelServiceDetailAction.EnabledChanged(false))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setInstanceEnabled("instance-openai", false) }
        coVerify(exactly = 0) { repository.saveInstance(any()) }
    }

    @Test
    fun deletingShowsImpactThenClearsThroughRepository() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        coEvery { repository.deletionImpact("instance-openai") } returns github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact(
            roles = listOf(DefaultModelRole.Assistant),
            hasTtsVoiceBinding = true,
            hasRealtimeVoiceBinding = false,
        )
        val viewModel = createViewModel(repository)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        viewModel.onAction(ModelServiceDetailAction.RequestDelete)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.deletionImpact?.hasImpact == true)

        viewModel.onAction(ModelServiceDetailAction.ConfirmDelete)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteInstance("instance-openai") }
    }

    @Test
    fun connectionTestResultIsInvalidatedByDraftEdits() = runTest {
        val repository = repository(instances = listOf(openAiInstance()))
        val gateway = mockk<ModelServiceRemoteGateway> {
            coEvery { validateConnection(any()) } returns ServiceConnectionResult.Succeeded
        }
        val viewModel = createViewModel(repository, gateway)
        viewModel.onAction(ModelServiceDetailAction.Load("instance-openai", null, null))
        advanceUntilIdle()

        viewModel.onAction(ModelServiceDetailAction.TestConnection("conn-conversation"))
        advanceUntilIdle()
        val editor = viewModel.uiState.value.connections.first { it.connection.id == "conn-conversation" }
        assertTrue(editor.test.isApplicable(viewModel.uiState.value.connectionTestVersion))
        assertEquals(ServiceConnectionResult.Succeeded, editor.test.result)

        // 修改草稿端点后旧结果不再适用
        viewModel.onAction(
            ModelServiceDetailAction.ConnectionEndpointChanged("conn-conversation", "https://relay.example.com"),
        )
        advanceUntilIdle()
        val staleEditor = viewModel.uiState.value.connections.first { it.connection.id == "conn-conversation" }
        assertFalse(staleEditor.test.isApplicable(viewModel.uiState.value.connectionTestVersion))
    }

    private fun createViewModel(
        repository: ModelCatalogRepository,
        gateway: ModelServiceRemoteGateway = mockk(),
    ): ModelServiceDetailViewModel {
        val voiceCapabilities = object : VoiceCapabilityResolver {
            override fun isSupported(templateId: String, capability: ModelCapability): Boolean = true
            override fun connectionProbe(templateId: String): SpeechConnectionProbe =
                throw VoiceCapabilityNotSupported(templateId, ModelCapability.TTS)
            override fun endpointStrategy(templateId: String): EndpointStrategy = EndpointStrategy.VendorOwned
            override fun realtimeSupport(templateId: String) = null
        }
        return ModelServiceDetailViewModel(
            officialToolSupport = github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport(),
            modelCatalog = repository,
            loadInstance = LoadModelInstanceUseCase(repository),
            observeInstance = ObserveModelInstanceUseCase(repository),
            saveNewInstance = SaveNewModelInstanceUseCase(repository),
            saveInstance = SaveModelInstanceUseCase(repository),
            setInstanceEnabled = SetModelInstanceEnabledUseCase(repository),
            deleteInstance = DeleteModelInstanceUseCase(repository),
            deletionImpact = DeletionImpactUseCase(repository),
            updateDefaults = UpdateDefaultModelSettingsUseCase(repository),
            testConnection = TestModelServiceConnectionUseCase(gateway),
            fetchCatalog = RefreshModelCatalogUseCase(gateway),
            runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
            voiceCapabilities = voiceCapabilities,
        )
    }

    private fun repository(
        instances: List<ModelServiceInstance>,
        instancesFlow: MutableStateFlow<List<ModelServiceInstance>>? = null,
        allDefaultsEmpty: Boolean = true,
    ): ModelCatalogRepository {
        val flow = instancesFlow ?: MutableStateFlow(instances)
        return mockk(relaxed = true) {
            coEvery { awaitReady() } returns Unit
            every { observeInstances() } returns flow
            every { observeInstance(any()) } answers {
                val id = firstArg<String>()
                flow.map { list -> list.firstOrNull { it.id == id } }
            }
            every { observeLoadState() } returns MutableStateFlow(
                github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState.Ready,
            )
            every { observeTemplates() } returns MutableStateFlow(listOf(template()))
            every { template("openai") } returns template()
            every { currentInstances() } returns flow.value
            every { currentInstance(any()) } answers {
                flow.value.firstOrNull { it.id == firstArg<String>() }
            }
            every { currentAssistantSelection() } returns null
            every { currentFastSelection() } returns null
            every { currentSpeechSelection() } returns null
            every { currentTtsSelection() } returns null
            every { currentRealtimeSelection() } returns null
            every { observeAssistantSelection() } returns MutableStateFlow(null)
            every { observeFastSelection() } returns MutableStateFlow(null)
            every { observeSpeechSelection() } returns MutableStateFlow(null)
            every { observeTtsSelection() } returns MutableStateFlow(null)
            every { observeTtsVoice() } returns MutableStateFlow("")
            every { observeRealtimeSelection() } returns MutableStateFlow(null)
            every { observeRealtimeVoice() } returns MutableStateFlow(null)
            coEvery { saveNewInstance(any()) } returns null
            coEvery { saveInstance(any()) } returns true
            coEvery { setInstanceEnabled(any(), any()) } returns true
            coEvery { deleteInstance(any()) } returns true
            coEvery { deletionImpact(any()) } answers {
                github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact(emptyList(), false, false)
            }
        }
    }

    private fun template() = ModelServiceTemplate(
        id = "openai",
        name = "OpenAI",
        connections = listOf(
            TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
                catalog = listOf(Model("gpt-4.1", "gpt-4.1")),
            ),
            TemplateConnection(
                capability = ServiceCapability.Recognition,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
                catalog = listOf(Model("whisper-1", "whisper-1", capabilities = setOf(ModelCapability.STT))),
            ),
            TemplateConnection(
                capability = ServiceCapability.Synthesis,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Realtime,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "wss://api.openai.com/v1/realtime"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("wss"),
            ),
        ),
    )

    private fun openAiInstance() = ModelServiceInstance(
        id = "instance-openai",
        name = "OpenAI 个人",
        templateId = "openai",
        isEnabled = true,
        isOfficialToolsEnabled = true,
        sharedApiKey = "sk-key",
        connections = listOf(
            ServiceConnection(
                id = "conn-conversation",
                capability = ServiceCapability.Conversation,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://api.openai.com/v1",
                models = listOf(Model("gpt-4.1", "gpt-4.1")),
            ),
            ServiceConnection(
                id = "conn-recognition",
                capability = ServiceCapability.Recognition,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://api.openai.com/v1",
                models = listOf(Model("whisper-1", "whisper-1", capabilities = setOf(ModelCapability.STT))),
            ),
            ServiceConnection(
                id = "conn-synthesis",
                capability = ServiceCapability.Synthesis,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://api.openai.com/v1",
            ),
            ServiceConnection(
                id = "conn-realtime",
                capability = ServiceCapability.Realtime,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "wss://api.openai.com/v1/realtime",
            ),
        ),
    )

    private companion object {
        const val R_error = github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_invalid_connection
    }
}
