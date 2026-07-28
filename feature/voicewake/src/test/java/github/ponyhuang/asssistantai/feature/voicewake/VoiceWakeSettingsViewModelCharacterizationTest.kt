package github.ponyhuang.asssistantai.feature.voicewake

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordError
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordException
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelState
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
import github.ponyhuang.asssistantai.domain.speech.repository.VoiceWakeRepository
import github.ponyhuang.asssistantai.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.asssistantai.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceWakeSettingsViewModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun configuredChatAndSelectedSpeechModelAllowPermissionRequestAndStart() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady || state.voiceState.model.status != WakeModelStatus.Ready) {
                state = awaitItem()
            }

            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(enabled = true))
            do {
                state = awaitItem()
            } while (state.permissionRequestId == null)

            val requestId = requireNotNull(state.permissionRequestId)
            viewModel.onAction(VoiceWakeSettingsAction.PermissionRequestHandled(requestId))
            viewModel.onAction(VoiceWakeSettingsAction.PermissionsResult(granted = true))

            verify(exactly = 1) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun missingWakeModelPromptsForManualInstallWithoutDownloading() = runTest {
        val voiceRepository = voiceRepository(ready = false)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.effects.test {
                viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(enabled = true))

                assertEquals(
                    VoiceWakeSettingsEffect.ShowToast(R.string.voicewake_model_download_prompt),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }

            verify(exactly = 0) { voiceRepository.installModel(any()) }
            assertEquals(null, viewModel.uiState.value.permissionRequestId)
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidKeywordSurfacesTypedValidationError() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        every { voiceRepository.setKeyword("x") } returns
            Result.failure(WakeKeywordException(WakeKeywordError.InvalidLength))
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(VoiceWakeSettingsAction.KeywordChanged("x"))
            var state = awaitItem()
            while (state.keywordDraft != "x") state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.SaveKeyword)
            do {
                state = awaitItem()
            } while (state.keywordError == null)

            assertEquals(WakeKeywordError.InvalidLength, state.keywordError)
            verify(exactly = 1) { voiceRepository.setKeyword("x") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingModelRestoresThatModelsSavedKeywordWithoutAutoInstalling() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.KeywordChanged("自定义唤醒词"))
            state = awaitItem()
            while (state.keywordDraft != "自定义唤醒词") state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.SelectModel(WakeModelCatalog.English.id))
            do {
                state = awaitItem()
            } while (state.voiceState.activeModelId != WakeModelCatalog.English.id)

            // 草稿丢弃，输入框回落为英语模型已保存（默认）的唤醒词。
            assertEquals(WakeModelCatalog.English.defaultKeyword, state.keywordDraft)
            assertNull(state.keywordError)
            // 英语模型未安装，选中后不自动触发安装；仅点击安装按钮才下载。
            verify(exactly = 0) { voiceRepository.installModel(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingInstalledModelDoesNotReinstall() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            // 中文模型已安装且已激活，重复选中不触发安装（状态不变，不会有新发射）。
            viewModel.onAction(VoiceWakeSettingsAction.SelectModel(WakeModelCatalog.Chinese.id))

            verify(exactly = 0) { voiceRepository.installModel(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun missingSelectedSpeechModelKeepsConfigurationBlocked() = runTest {
        val repository = modelRepository(speechSelection = null)
        val voiceRepository = voiceRepository(ready = true)
        val viewModel = viewModel(repository, voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.voiceState.model.status != WakeModelStatus.Ready) state = awaitItem()
            assertTrue(!state.configurationReady)

            viewModel.onAction(VoiceWakeSettingsAction.PermissionsResult(granted = true))
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(
        models: ModelCatalogRepository,
        voice: VoiceWakeRepository,
    ): VoiceWakeSettingsViewModel {
        val observeDefaults = ObserveDefaultModelSettingsUseCase(models)
        return VoiceWakeSettingsViewModel(
            observeSettings = ObserveVoiceWakeSettingsUseCase(observeDefaults, voice),
            manageVoiceWake = ManageVoiceWakeUseCase(voice),
        )
    }

    private fun voiceRepository(ready: Boolean): VoiceWakeRepository {
        val flow = MutableStateFlow(
            VoiceWakeState(
                availableModels = WakeModelCatalog.models,
                activeModelId = WakeModelCatalog.Chinese.id,
                modelStates = mapOf(
                    WakeModelCatalog.Chinese.id to WakeModelState(
                        status = if (ready) WakeModelStatus.Ready else WakeModelStatus.Missing,
                    ),
                    WakeModelCatalog.English.id to WakeModelState(WakeModelStatus.Missing),
                ),
            ),
        )
        return mockk(relaxed = true) {
            every { state } returns flow
            every { setKeyword(any()) } returns Result.success(Unit)
            every { selectModel(any()) } answers {
                val info = WakeModelCatalog.byId(firstArg()) ?: return@answers Unit
                flow.value = flow.value.copy(activeModelId = info.id, keyword = info.defaultKeyword)
            }
        }
    }

    private fun modelRepository(
        speechSelection: ModelSelection? = ModelSelection("service", "group", "stt"),
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns MutableStateFlow(listOf(service()))
        every { observeAssistantSelection() } returns MutableStateFlow(null)
        every { observeFastSelection() } returns MutableStateFlow(null)
        every { observeSpeechSelection() } returns MutableStateFlow(speechSelection)
        every { observeTtsSelection() } returns MutableStateFlow(null)
        every { observeTtsVoice() } returns MutableStateFlow("")
    }

    private fun service() = LLMModelSetting(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = listOf(
            ModelGroup(
                id = "group",
                name = "Group",
                models = listOf(
                    Model(id = "chat", name = "Chat"),
                    Model(id = "stt", name = "STT", isStt = true),
                ),
            ),
        ),
    )
}
