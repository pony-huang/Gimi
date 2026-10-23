package github.ponyhuang.gimi.feature.modelsettings.detail

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeleteModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelCandidatesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelServiceEnabledUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.feature.modelsettings.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 草稿与运行时配置之间的回归边界，防止取消编辑、失败保存或刷新候选改变真实配置。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceDetailViewLLMModelCharacterizationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadServiceFlattensModelsWithoutGroupHeaders() = runTest {
        val fixture = fixture(service())
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Load("deepseek"))
        advanceUntilIdle()
        assertEquals(listOf(LLMModelSettingDetailRow.LLMModelItem("chat", Model("deepseek-chat", "deepseek-chat"))),
            fixture.viewModel.uiState.value.rows)
    }

    @Test
    fun externalCatalogUpdateIsVisibleOnlyWithoutUnsavedEdits() = runTest {
        val fixture = fixture(service())
        fixture.load()
        val incoming = service().copy(groups = listOf(ModelGroup("chat", "Chat", listOf(Model("new", "New")))))
        fixture.services.value = incoming
        advanceUntilIdle()
        assertEquals("new", fixture.viewModel.uiState.value.rows.single().model.id)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("unsaved"))
        fixture.services.value = service()
        advanceUntilIdle()
        assertEquals("unsaved", fixture.viewModel.uiState.value.service?.apiKey)
        assertEquals("new", fixture.viewModel.uiState.value.rows.single().model.id)
    }

    @Test
    fun missingServiceRequestsCloseAndShowsExistingMessage() = runTest {
        val fixture = fixture(null)
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.Load("missing"))
            assertEquals(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.SettingNotFoundLLM), awaitItem())
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
        }
        assertFalse(fixture.viewModel.uiState.value.isLoading)
    }

    @Test
    fun blankApiKeyReturnsFailureWithoutRequest() = runTest {
        val fixture = fixture(service().copy(apiKey = "", isAdded = true))
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.TestConnection)
        advanceUntilIdle()
        assertEquals(false, fixture.viewModel.uiState.value.connectionResult)
        coVerify(exactly = 0) { fixture.remote.validateConnection(any()) }
    }

    @Test
    fun apiKeyAndToolPreferenceChangesRemainInDraftUntilSave() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("new"))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OfficialToolsEnabledChanged(false))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.FileUploadEnabledChanged(true))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.FileUploadRetentionChanged(30))
        advanceUntilIdle()
        assertEquals("new", fixture.viewModel.uiState.value.service?.apiKey)
        assertFalse(fixture.viewModel.uiState.value.service!!.isOfficialToolsEnabled)
        assertEquals("key", fixture.services.value?.apiKey)
        assertFalse(fixture.services.value!!.isFileUploadEnabled)
        assertEquals(7, fixture.services.value!!.fileUploadRetentionDays)
        assertTrue(fixture.services.value!!.isOfficialToolsEnabled)
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun protocolChangeToUnsupportedProtocolIsRejected() = runTest {
        val fixture = fixture(service().copy(supportedProtocols = listOf(ApiProtocol.OpenaiChatcompletions)))
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiProtocolChanged(ApiProtocol.Anthropic))
        assertEquals(ApiProtocol.OpenaiChatcompletions, fixture.viewModel.uiState.value.service?.apiProtocol)
        assertFalse(fixture.viewModel.uiState.value.isDirty)
    }

    @Test
    fun protocolChangesKeepSeparateAddressesAndOnlyModifyDraft() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiProtocolChanged(ApiProtocol.Anthropic))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiBaseUrlChanged("https://custom.example/anthropic"))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiProtocolChanged(ApiProtocol.OpenaiChatcompletions))
        val draft = fixture.viewModel.uiState.value.service!!
        assertEquals("https://api.deepseek.com", draft.activeApiBaseUrl)
        assertEquals("https://custom.example/anthropic", draft.anthropicBaseUrl)
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun refreshCandidatesPreservesSelectionsAndSpeechMetadataWithoutSaving() = runTest {
        val speech = Model("speech", "Speech", isTts = true)
        val fixture = fixture(service().copy(groups = listOf(ModelGroup("chat", "Chat", listOf(Model("deepseek-chat", "deepseek-chat"), speech)))))
        fixture.load()
        coEvery { fixture.remote.fetchModels(any()) } returns listOf(Model("speech", "speech"), Model("new", "New"))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        val before = fixture.viewModel.uiState.value.pendingModelKeys
        fixture.viewModel.onAction(LLMModelSettingDetailAction.RefreshModels)
        advanceUntilIdle()
        val state = fixture.viewModel.uiState.value
        assertEquals(before, state.pendingModelKeys)
        assertTrue(state.candidates.first { it.model.id == "speech" }.model.isTts)
        assertTrue(state.candidates.any { it.model.id == "new" })
        assertEquals(2, state.rows.size)
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun goingBackFromModelSelectionCancelsPendingChecks() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ToggleModel(ServiceModelKey("chat", "deepseek-chat")))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Back)
        assertEquals(1, fixture.viewModel.uiState.value.rows.size)
        assertFalse(fixture.viewModel.uiState.value.isDirty)
    }

    @Test
    fun modelConfirmationCanExplicitlySelectZeroModels() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ToggleModel(ServiceModelKey("chat", "deepseek-chat")))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmModels)
        assertTrue(fixture.viewModel.uiState.value.rows.isEmpty())
        assertTrue(fixture.viewModel.uiState.value.isDirty)
        assertEquals(1, fixture.services.value!!.groups.single().models.size)
    }

    @Test
    fun newServiceHasNoSelectedModelsAndCancellingDoesNotCreateConfiguration() = runTest {
        val template = service().copy(apiKey = "", isAdded = false, isEnabled = false)
        val fixture = fixture(template)
        fixture.load()
        assertTrue(fixture.viewModel.uiState.value.rows.isEmpty())
        assertEquals(1, fixture.viewModel.uiState.value.candidates.size)
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.Back)
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
        }
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun dirtyBackPromptsAndDiscardDoesNotSave() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("new"))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Back)
        assertTrue(fixture.viewModel.uiState.value.isDiscardDialogVisible)
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.Discard)
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
        }
        assertEquals("key", fixture.services.value?.apiKey)
    }

    @Test
    fun savePublishesTheWholeDraftOnceAndClosesOnlyAfterSuccess() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("new"))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OfficialToolsEnabledChanged(false))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.FileUploadEnabledChanged(true))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.FileUploadRetentionChanged(30))
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.Save)
            assertEquals(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.Saved), awaitItem())
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
        }
        coVerify(exactly = 1) { fixture.repository.saveService(match { it.apiKey == "new" && !it.isOfficialToolsEnabled && it.isFileUploadEnabled && it.fileUploadRetentionDays == 30 }) }
    }

    @Test
    fun failedSaveKeepsDraftAndOriginalAndDoesNotClose() = runTest {
        val fixture = fixture(service())
        fixture.load()
        coEvery { fixture.repository.saveService(any()) } returns false
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("unsaved"))
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.Save)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals("key", fixture.services.value?.apiKey)
        assertEquals("unsaved", fixture.viewModel.uiState.value.service?.apiKey)
        assertEquals(R.string.modelsettings_write_failed, fixture.viewModel.uiState.value.errorRes)
        assertFalse(fixture.viewModel.uiState.value.isSaving)
    }

    @Test
    fun changingConnectionCancelsTestAndClearsItsResult() = runTest {
        val fixture = fixture(service())
        fixture.load()
        coEvery { fixture.remote.validateConnection(any()) } coAnswers { suspendCancellableCoroutine { } }
        fixture.viewModel.onAction(LLMModelSettingDetailAction.TestConnection)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.isTestingKey)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("new"))
        advanceUntilIdle()
        assertNull(fixture.viewModel.uiState.value.connectionResult)
        assertFalse(fixture.viewModel.uiState.value.isTestingKey)
    }

    @Test
    fun activeAgentPreventsSavePauseAndDeleteEvenIfItStartsImmediatelyBeforeWrite() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(service(), gate)
        fixture.load()
        gate.state.value = FakeAgentRuntimeGate.busy().state.value
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Save)
        advanceUntilIdle()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.EnabledChanged(false))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmDelete)
        advanceUntilIdle()
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
        coVerify(exactly = 0) { fixture.repository.setServiceEnabled(any(), any()) }
        coVerify(exactly = 0) { fixture.repository.deleteService(any()) }
    }

    @Test
    fun pauseAndResumeKeepDraftToolPreference() = runTest {
        val fixture = fixture(service().copy(isOfficialToolsEnabled = false))
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.EnabledChanged(false))
        advanceUntilIdle()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.EnabledChanged(true))
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.service!!.isOfficialToolsEnabled)
        coVerify { fixture.repository.setServiceEnabled("deepseek", false) }
        coVerify { fixture.repository.setServiceEnabled("deepseek", true) }
    }

    @Test
    fun deletionIsConfirmedBeforeRepositoryMutation() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.RequestDelete)
        assertTrue(fixture.viewModel.uiState.value.isDeleteDialogVisible)
        coVerify(exactly = 0) { fixture.repository.deleteService(any()) }
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmDelete)
            assertEquals(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.Deleted), awaitItem())
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
        }
    }

    @Test
    fun closeIsBufferedBeforeRouteCollectionAndRepeatedBackDoesNotPopTwice() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Back)
        advanceUntilIdle()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Back)
        fixture.viewModel.effects.test {
            assertEquals(LLMModelSettingDetailEffect.Close, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun selectingCatalogModelsPreservesStableGroupIdAndDisplayName() = runTest {
        val fixture = fixture(service())
        coEvery { fixture.repository.modelCandidates(any()) } returns service().groups +
            ModelGroup("speech", "Speech models", listOf(Model("voice", "Voice", isTts = true)))
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ToggleModel(ServiceModelKey("speech", "voice")))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmModels)
        val group = fixture.viewModel.uiState.value.service!!.groups.first { it.id == "speech" }
        assertEquals("Speech models", group.name)
        assertTrue(group.models.single().isTts)
    }

    @Test
    fun anyChatCompletionsServiceCanEnableFileUploadWithoutAProviderAllowlist() = runTest {
        val fixture = fixture(service().copy(id = "custom", apiProtocol = ApiProtocol.OpenaiChatcompletions))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.Load("custom"))
        advanceUntilIdle()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.FileUploadEnabledChanged(true))
        assertTrue(fixture.viewModel.uiState.value.service!!.isFileUploadEnabled)
        assertFalse(fixture.services.value!!.isFileUploadEnabled)
    }

    @Test
    fun bulkSelectionKeepsSpeechMetadataAndOnlyPersistsAfterSave() = runTest {
        val configured = service().copy(groups = listOf(
            ModelGroup("chat", "Chat", listOf(Model("chat", "Chat"))),
            ModelGroup("speech", "Speech", listOf(Model("stt", "STT", isStt = true), Model("tts", "TTS", isTts = true))),
        ))
        val fixture = fixture(configured)
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ClearModels)
        assertTrue(fixture.viewModel.uiState.value.pendingModelKeys.isEmpty())
        assertEquals(3, fixture.viewModel.uiState.value.rows.size)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.SelectAllModels)
        assertEquals(3, fixture.viewModel.uiState.value.pendingModelKeys.size)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmModels)
        assertEquals(configured.groups, fixture.viewModel.uiState.value.service!!.groups)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ClearModels)
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ConfirmModels)
        assertTrue(fixture.viewModel.uiState.value.service!!.groups.isEmpty())
        assertEquals(configured.groups, fixture.services.value!!.groups)
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun busyAgentBlocksBothBulkSelectionActions() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(service(), gate)
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        val selected = fixture.viewModel.uiState.value.pendingModelKeys
        gate.state.value = FakeAgentRuntimeGate.busy().state.value
        runCurrent()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ClearModels)
        assertEquals(selected, fixture.viewModel.uiState.value.pendingModelKeys)
        gate.state.value = FakeAgentRuntimeGate().state.value
        runCurrent()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.ClearModels)
        gate.state.value = FakeAgentRuntimeGate.busy().state.value
        runCurrent()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.SelectAllModels)
        assertTrue(fixture.viewModel.uiState.value.pendingModelKeys.isEmpty())
    }

    @Test
    fun refreshReportsValidDistinctRemoteCountWithoutChangingSelections() = runTest {
        val fixture = fixture(service())
        fixture.load()
        fixture.viewModel.onAction(LLMModelSettingDetailAction.OpenPage(ServiceEditorPage.Models))
        val selected = fixture.viewModel.uiState.value.pendingModelKeys
        coEvery { fixture.remote.fetchModels(any()) } returns listOf(Model("new", "New"), Model("new", "Duplicate"), Model("", "Invalid"))
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.RefreshModels)
            assertEquals(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.ModelsRefreshed(1)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(selected, fixture.viewModel.uiState.value.pendingModelKeys)
        assertEquals(2, fixture.viewModel.uiState.value.candidates.size)
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test
    fun emptyRefreshReportsZeroAndCancelledRefreshDoesNotReportSuccess() = runTest {
        val fixture = fixture(service())
        fixture.load()
        coEvery { fixture.remote.fetchModels(any()) } returns emptyList()
        fixture.viewModel.effects.test {
            fixture.viewModel.onAction(LLMModelSettingDetailAction.RefreshModels)
            assertEquals(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.ModelsRefreshed(0)), awaitItem())
            assertEquals(R.string.modelsettings_no_remote_models, fixture.viewModel.uiState.value.errorRes)
            coEvery { fixture.remote.fetchModels(any()) } coAnswers { suspendCancellableCoroutine { } }
            fixture.viewModel.onAction(LLMModelSettingDetailAction.RefreshModels)
            runCurrent()
            fixture.viewModel.onAction(LLMModelSettingDetailAction.ApiKeyChanged("changed"))
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun fixture(service: LLMModelSetting?, gate: FakeAgentRuntimeGate = FakeAgentRuntimeGate()): Fixture {
        val services = MutableStateFlow(service)
        val repository = mockk<ModelCatalogRepository>(relaxed = true) {
            every { currentService(any()) } answers { services.value?.takeIf { it.id == firstArg() } }
            every { observeService(any()) } returns services
            coEvery { modelCandidates(any()) } returns service?.groups.orEmpty()
            coEvery { saveService(any()) } coAnswers { services.value = firstArg(); true }
            coEvery { setServiceEnabled(any(), any()) } returns true
            coEvery { deleteService(any()) } returns true
        }
        val remote = mockk<ModelServiceRemoteGateway>(relaxed = true)
        val vm = ModelServiceDetailViewModel(
            OfficialToolSupport(), LoadModelServiceUseCase(repository), ObserveModelServiceUseCase(repository),
            LoadModelCandidatesUseCase(repository), SaveModelServiceUseCase(repository), DeleteModelServiceUseCase(repository),
            SetModelServiceEnabledUseCase(repository), TestModelServiceConnectionUseCase(remote),
            RefreshModelCatalogUseCase(remote), RunWhenAgentIdleUseCase(gate),
        )
        return Fixture(vm, repository, remote, services)
    }

    private suspend fun Fixture.load() {
        viewModel.onAction(LLMModelSettingDetailAction.Load("deepseek"))
        // 调用方的测试调度器会驱动 ViewModel；直接等待 state 避免阻塞线程。
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertNotNull(state.service)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun service() = LLMModelSetting("deepseek", "DeepSeek", true, apiKey = "key",
        apiBaseUrl = "https://api.deepseek.com", apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "https://api.deepseek.com/anthropic",
        groups = listOf(ModelGroup("chat", "Chat", listOf(Model("deepseek-chat", "deepseek-chat")))))

    /** 仓库流用于区分未保存草稿与真实配置。 */
    private data class Fixture(
        val viewModel: ModelServiceDetailViewModel,
        val repository: ModelCatalogRepository,
        val remote: ModelServiceRemoteGateway,
        val services: MutableStateFlow<LLMModelSetting?>,
    )
}
