package github.ponyhuang.gimi.feature.voicewake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.gimi.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceWakeSettingsViewModel @Inject constructor(
    observeSettings: ObserveVoiceWakeSettingsUseCase,
    private val manageVoiceWake: ManageVoiceWakeUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())
    private var nextPermissionRequestId = 0
    private val settings = observeSettings()

    val uiState = combine(settings, localState) { settings, local ->
        VoiceWakeSettingsUiState(
            voiceState = settings.voiceState,
            configurationReady = settings.configurationReady,
            isStartPending = local.isStartPending,
            permissionRequestId = local.permissionRequestId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceWakeSettingsUiState(),
    )

    init {
        viewModelScope.launch {
            settings.collect { state ->
                if (
                    state.configurationReady &&
                    state.voiceState.model.status == WakeModelStatus.Ready
                ) {
                    localState.update { local ->
                        if (local.isStartPending) {
                            local.copy(
                                isStartPending = false,
                                permissionRequestId = ++nextPermissionRequestId,
                            )
                        } else {
                            local
                        }
                    }
                }
            }
        }
    }

    fun onAction(action: VoiceWakeSettingsAction) {
        when (action) {
            is VoiceWakeSettingsAction.ToggleListening -> toggleListening(action.enabled)
            is VoiceWakeSettingsAction.SelectModel -> selectModel(action.modelId)
            is VoiceWakeSettingsAction.InstallModel -> manageVoiceWake.installModel(action.modelId)
            is VoiceWakeSettingsAction.CancelInstall -> {
                if (action.modelId == uiState.value.voiceState.activeModelId) {
                    localState.update { it.copy(isStartPending = false) }
                }
                manageVoiceWake.cancelInstall(action.modelId)
            }
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

    private fun selectModel(modelId: String) {
        val targetStatus = uiState.value.voiceState.modelStates[modelId]?.status
        manageVoiceWake.selectModel(modelId)
        if (targetStatus == WakeModelStatus.Missing || targetStatus == WakeModelStatus.Error) {
            manageVoiceWake.installModel(modelId)
        }
    }

    private fun toggleListening(enabled: Boolean) {
        if (!enabled) {
            val wasRunning = uiState.value.voiceState.isRunning
            localState.update { it.copy(isStartPending = false) }
            if (wasRunning) manageVoiceWake.stop()
            return
        }

        val state = uiState.value
        when (state.voiceState.model.status) {
            WakeModelStatus.Missing,
            WakeModelStatus.Error,
            -> {
                localState.update { it.copy(isStartPending = true) }
                manageVoiceWake.installModel(state.voiceState.activeModelId)
            }
            WakeModelStatus.Downloading,
            WakeModelStatus.Extracting,
            -> localState.update { it.copy(isStartPending = true) }
            WakeModelStatus.Ready -> if (state.configurationReady) localState.update {
                it.copy(permissionRequestId = ++nextPermissionRequestId)
            }
        }
    }

    /** 仅由设置页内部消费的一次性权限请求状态。 */
    private data class LocalState(
        val isStartPending: Boolean = false,
        val permissionRequestId: Int? = null,
    )
}
