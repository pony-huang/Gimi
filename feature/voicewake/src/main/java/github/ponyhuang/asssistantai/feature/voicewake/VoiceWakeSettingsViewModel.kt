package github.ponyhuang.asssistantai.feature.voicewake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
import github.ponyhuang.asssistantai.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.asssistantai.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceWakeSettingsUiState(),
    )

    fun onAction(action: VoiceWakeSettingsAction) {
        when (action) {
            is VoiceWakeSettingsAction.KeywordSelected ->
                manageVoiceWake.setKeyword(action.keyword)
            is VoiceWakeSettingsAction.ToggleListening -> toggleListening(action.enabled)
            VoiceWakeSettingsAction.InstallModel -> manageVoiceWake.installModel()
            is VoiceWakeSettingsAction.PermissionsResult -> {
                val state = uiState.value
                if (
                    action.granted &&
                    state.configurationReady &&
                    state.voiceState.model.status == WakeModelStatus.Ready
                ) {
                    manageVoiceWake.start()
                }
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
            manageVoiceWake.stop()
            return
        }

        val state = uiState.value
        when {
            state.voiceState.model.status != WakeModelStatus.Ready ->
                manageVoiceWake.installModel()
            !state.configurationReady -> Unit
            else -> localState.update {
                it.copy(permissionRequestId = ++nextPermissionRequestId)
            }
        }
    }

    private data class LocalState(
        val permissionRequestId: Int? = null,
    )
}
