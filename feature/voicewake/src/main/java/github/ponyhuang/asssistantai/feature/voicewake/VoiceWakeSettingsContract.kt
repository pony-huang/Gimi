package github.ponyhuang.asssistantai.feature.voicewake

import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState

data class VoiceWakeSettingsUiState(
    val voiceState: VoiceWakeState = VoiceWakeState(),
    val configurationReady: Boolean = false,
    val permissionRequestId: Int? = null,
)

sealed interface VoiceWakeSettingsAction {
    data class KeywordSelected(val keyword: String) : VoiceWakeSettingsAction
    data class ToggleListening(val enabled: Boolean) : VoiceWakeSettingsAction
    data object InstallModel : VoiceWakeSettingsAction
    data class PermissionsResult(val granted: Boolean) : VoiceWakeSettingsAction
    data class PermissionRequestHandled(val requestId: Int) : VoiceWakeSettingsAction
}
