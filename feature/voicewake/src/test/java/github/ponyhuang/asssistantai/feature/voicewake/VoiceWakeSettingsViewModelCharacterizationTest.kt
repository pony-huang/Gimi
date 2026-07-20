package github.ponyhuang.asssistantai.feature.voicewake

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
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
    fun missingWakeModelInstallsInsteadOfRequestingPermissions() = runTest {
        val voiceRepository = voiceRepository(ready = false)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(enabled = true))

            verify(exactly = 1) { voiceRepository.installModel() }
            assertEquals(null, viewModel.uiState.value.permissionRequestId)
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidKeywordSurfacesControllerValidationMessage() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        every { voiceRepository.setKeyword("x") } returns
            Result.failure(IllegalArgumentException("唤醒词需要包含 2–20 个字符"))
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

            assertEquals("唤醒词需要包含 2–20 个字符", state.keywordError)
            verify(exactly = 1) { voiceRepository.setKeyword("x") }
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

    private fun voiceRepository(ready: Boolean): VoiceWakeRepository = mockk(relaxed = true) {
        every { state } returns MutableStateFlow(
            VoiceWakeState(
                model = WakeModelState(
                    status = if (ready) WakeModelStatus.Ready else WakeModelStatus.Missing,
                ),
            ),
        )
        every { setKeyword(any()) } returns Result.success(Unit)
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

    private fun service() = ModelService(
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
