package github.ponyhuang.gimi.feature.voicewake

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelState
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import github.ponyhuang.gimi.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.gimi.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun enablingReadyWakeModelKeepsSwitchPendingUntilListenerStarts() = runTest {
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

            assertTrue(state.isStartPending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listenerStartingClearsPendingWithoutRequestingPermissionAgain() = runTest {
        val voiceState = voiceState(ready = true)
        val voiceRepository = voiceRepository(voiceState)
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
            voiceState.value = voiceState.value.copy(status = VoiceWakeStatus.Starting)

            do {
                state = awaitItem()
            } while (state.isStartPending)

            assertEquals(null, state.permissionRequestId)
            verify(exactly = 1) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deniedPermissionClearsPendingStart() = runTest {
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

            viewModel.onAction(
                VoiceWakeSettingsAction.PermissionRequestHandled(
                    requireNotNull(state.permissionRequestId),
                ),
            )
            viewModel.onAction(VoiceWakeSettingsAction.PermissionsResult(granted = false))

            do {
                state = awaitItem()
            } while (state.isStartPending)

            assertFalse(state.isStartPending)
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listenerErrorClearsPendingWithoutRequestingPermissionAgain() = runTest {
        val voiceState = voiceState(ready = true)
        val voiceRepository = voiceRepository(voiceState)
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
            voiceState.value = voiceState.value.copy(status = VoiceWakeStatus.Error)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStartPending)
            assertEquals(null, viewModel.uiState.value.permissionRequestId)
            verify(exactly = 1) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun enablingMissingWakeModelStartsDownloadWithoutRequestingPermission() = runTest {
        val voiceRepository = voiceRepository(ready = false)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(enabled = true))

            verify(exactly = 1) { voiceRepository.installModel(WakeModelCatalog.Chinese.id) }
            assertEquals(null, viewModel.uiState.value.permissionRequestId)
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun enablingMissingWakeModelRequestsPermissionAfterDownloadCompletes() = runTest {
        val voiceState = voiceState(ready = false)
        val voiceRepository = voiceRepository(voiceState)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(enabled = true))
            voiceState.value = voiceState.value.copy(
                modelStates = voiceState.value.modelStates + (
                    WakeModelCatalog.Chinese.id to WakeModelState(WakeModelStatus.Ready, 1f)
                ),
            )

            do {
                state = awaitItem()
            } while (state.permissionRequestId == null)

            assertEquals(1, state.permissionRequestId)
            verify(exactly = 0) { voiceRepository.start() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingMissingModelStartsItsDownload() = runTest {
        val voiceRepository = voiceRepository(ready = true)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady) state = awaitItem()

            viewModel.onAction(VoiceWakeSettingsAction.SelectModel(WakeModelCatalog.English.id))

            verify(exactly = 1) { voiceRepository.installModel(WakeModelCatalog.English.id) }
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
    fun removingActiveModelStopsListeningBeforeDeletingModel() = runTest {
        val voiceState = voiceState(ready = true).apply {
            value = value.copy(status = VoiceWakeStatus.Listening)
        }
        val voiceRepository = voiceRepository(voiceState)
        val viewModel = viewModel(modelRepository(), voiceRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.configurationReady || !state.voiceState.isRunning) state = awaitItem()

            viewModel.onAction(
                VoiceWakeSettingsAction.RemoveModel(WakeModelCatalog.Chinese.id),
            )

            verifyOrder {
                voiceRepository.stop()
                voiceRepository.removeModel(WakeModelCatalog.Chinese.id)
            }
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
        return voiceRepository(voiceState(ready))
    }

    private fun voiceRepository(flow: MutableStateFlow<VoiceWakeState>): VoiceWakeRepository {
        return mockk(relaxed = true) {
            every { state } returns flow
            every { selectModel(any()) } answers {
                val info = WakeModelCatalog.byId(firstArg()) ?: return@answers Unit
                flow.value = flow.value.copy(activeModelId = info.id)
            }
        }
    }

    private fun voiceState(ready: Boolean) = MutableStateFlow(
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
