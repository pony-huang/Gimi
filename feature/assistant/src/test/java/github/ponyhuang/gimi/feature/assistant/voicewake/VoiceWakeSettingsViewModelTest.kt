package github.ponyhuang.gimi.feature.assistant.voicewake

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeSettings
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.WakePhraseError
import github.ponyhuang.gimi.domain.speech.model.WakePhraseException
import github.ponyhuang.gimi.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.gimi.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class VoiceWakeSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun availableRecognizerRequestsPermissionThenEnablesAfterGrant() = runTest {
        val manage = manager()
        val viewModel = viewModel(
            VoiceWakeSettings(
                VoiceWakeState(recognizerAvailable = true),
                configurationReady = true,
            ),
            manage,
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.voiceState.recognizerAvailable || !state.configurationReady) {
                state = awaitItem()
            }
            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(true))
            val request = awaitItem().permissionRequestId
            viewModel.onAction(VoiceWakeSettingsAction.PermissionResult(true))

            verify(exactly = 1) { manage.setEnabled(true) }
            assertEquals(1, request)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unavailableRecognizerDoesNotRequestPermissionOrEnable() = runTest {
        val manage = manager()
        val viewModel = viewModel(
            VoiceWakeSettings(
                VoiceWakeState(recognizerAvailable = false),
                configurationReady = true,
            ),
            manage,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(true))

            assertNull(viewModel.uiState.value.permissionRequestId)
            verify(exactly = 0) { manage.setEnabled(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun enabledWakeCanAlwaysBeDisabledAfterChatConfigurationIsRemoved() = runTest {
        val manage = manager()
        val viewModel = viewModel(
            VoiceWakeSettings(
                VoiceWakeState(enabled = true, recognizerAvailable = true),
                configurationReady = false,
            ),
            manage,
        )

        viewModel.onAction(VoiceWakeSettingsAction.ToggleListening(false))

        verify(exactly = 1) { manage.setEnabled(false) }
    }

    @Test
    fun duplicatePhraseSurfacesTypedErrorAndKeepsDraft() = runTest {
        val manage = manager()
        every { manage.addTriggerPhrase("吉米") } returns
            Result.failure(WakePhraseException(WakePhraseError.Duplicate))
        val viewModel = viewModel(
            VoiceWakeSettings(
                VoiceWakeState(recognizerAvailable = true),
                configurationReady = true,
            ),
            manage,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(VoiceWakeSettingsAction.PhraseChanged("吉米"))
            awaitItem()
            viewModel.onAction(VoiceWakeSettingsAction.AddPhrase)
            val failed = awaitItem()

            assertEquals("吉米", failed.phraseDraft)
            assertEquals(WakePhraseError.Duplicate, failed.phraseError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun manager(): ManageVoiceWakeUseCase = mockk(relaxed = true) {
        every { addTriggerPhrase(any()) } returns Result.success(Unit)
        every { removeTriggerPhrase(any()) } returns Result.success(Unit)
    }

    private fun viewModel(
        settings: VoiceWakeSettings,
        manage: ManageVoiceWakeUseCase,
    ): VoiceWakeSettingsViewModel {
        val observe = mockk<ObserveVoiceWakeSettingsUseCase>()
        every { observe.invoke() } returns MutableStateFlow(settings)
        return VoiceWakeSettingsViewModel(observe, manage)
    }
}
