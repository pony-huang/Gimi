package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.speech.model.WakePhraseException
import github.ponyhuang.gimi.domain.speech.model.normalizeWakePhrase
import github.ponyhuang.gimi.domain.speech.model.validateWakePhrase
import github.ponyhuang.gimi.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.gimi.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class VoiceWakeSettingsViewModel @Inject constructor(
    observeSettings: ObserveVoiceWakeSettingsUseCase,
    private val manageVoiceWake: ManageVoiceWakeUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())
    private var nextPermissionRequestId = 0

    val uiState = combine(observeSettings(), localState) { settings, local ->
        VoiceWakeSettingsUiState(
            voiceState = settings.voiceState,
            configurationReady = settings.configurationReady,
            permissionRequestId = local.permissionRequestId,
            phraseDraft = local.phraseDraft,
            phraseError = local.phraseError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceWakeSettingsUiState(),
    )

    fun onAction(action: VoiceWakeSettingsAction) {
        when (action) {
            is VoiceWakeSettingsAction.ToggleListening -> toggleListening(action.enabled)
            is VoiceWakeSettingsAction.PhraseChanged -> localState.update {
                it.copy(phraseDraft = action.value, phraseError = validateWakePhrase(action.value))
            }
            VoiceWakeSettingsAction.AddPhrase -> addPhrase()
            is VoiceWakeSettingsAction.RemovePhrase -> manageVoiceWake.removeTriggerPhrase(action.phrase)
                .onSuccess { localState.update { it.copy(phraseError = null) } }
                .onFailure(::publishPhraseError)
            is VoiceWakeSettingsAction.PermissionResult -> {
                if (action.granted) manageVoiceWake.setEnabled(true)
                localState.update { it.copy(permissionRequestId = null) }
            }
            is VoiceWakeSettingsAction.PermissionRequestHandled -> localState.update {
                if (it.permissionRequestId == action.requestId) {
                    it.copy(permissionRequestId = null)
                } else {
                    it
                }
            }
        }
    }

    private fun toggleListening(enabled: Boolean) {
        if (!enabled) {
            manageVoiceWake.setEnabled(false)
            return
        }
        val state = uiState.value
        if (!state.voiceState.recognizerAvailable || !state.configurationReady) return
        localState.update { it.copy(permissionRequestId = ++nextPermissionRequestId) }
    }

    private fun addPhrase() {
        val normalized = normalizeWakePhrase(localState.value.phraseDraft)
        val error = validateWakePhrase(normalized)
        if (error != null) {
            localState.update { it.copy(phraseError = error) }
            return
        }
        manageVoiceWake.addTriggerPhrase(normalized)
            .onSuccess { localState.update { LocalState() } }
            .onFailure(::publishPhraseError)
    }

    private fun publishPhraseError(error: Throwable) {
        val typed = (error as? WakePhraseException)?.error ?: return
        localState.update { it.copy(phraseError = typed) }
    }

    /** 设置页内部的一次性权限请求和短语编辑状态。 */
    private data class LocalState(
        val permissionRequestId: Int? = null,
        val phraseDraft: String = "",
        val phraseError: github.ponyhuang.gimi.domain.speech.model.WakePhraseError? = null,
    )
}
