package github.ponyhuang.asssistantai.feature.assistant

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSystemStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    private val systemStatus: AssistantSystemStatusRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AssistantSettingsUiState(
            roleAvailable = systemStatus.isAssistantRoleAvailable(),
            isDefaultAssistant = systemStatus.isDefaultAssistant(),
        ),
    )
    val uiState: StateFlow<AssistantSettingsUiState> = _uiState.asStateFlow()

    fun onAction(action: AssistantSettingsAction) {
        when (action) {
            AssistantSettingsAction.RefreshStatus -> _uiState.update {
                it.copy(
                    roleAvailable = systemStatus.isAssistantRoleAvailable(),
                    isDefaultAssistant = systemStatus.isDefaultAssistant(),
                )
            }
            is AssistantSettingsAction.MicrophonePermissionResult -> _uiState.update {
                it.copy(microphoneGranted = action.granted)
            }
            AssistantSettingsAction.TileAddRequested -> _uiState.update {
                it.copy(tileAddRequested = true)
            }
        }
    }

    /** 麦克风权限由 Route 检查后直接同步。 */
    fun onMicrophonePermissionKnown(granted: Boolean) {
        _uiState.update { it.copy(microphoneGranted = granted) }
    }
}
