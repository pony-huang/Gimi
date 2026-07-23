package github.ponyhuang.asssistantai.feature.voicewake

import github.ponyhuang.asssistantai.domain.speech.model.DEFAULT_WAKE_KEYWORD
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordError

data class VoiceWakeSettingsUiState(
    val voiceState: VoiceWakeState = VoiceWakeState(),
    val configurationReady: Boolean = false,
    val keywordDraft: String = DEFAULT_WAKE_KEYWORD,
    val keywordError: WakeKeywordError? = null,
    val permissionRequestId: Int? = null,
)

sealed interface VoiceWakeSettingsAction {
    data class KeywordChanged(val value: String) : VoiceWakeSettingsAction
    data object SaveKeyword : VoiceWakeSettingsAction
    data class ToggleListening(val enabled: Boolean) : VoiceWakeSettingsAction
    data class SelectModel(val modelId: String) : VoiceWakeSettingsAction
    data class InstallModel(val modelId: String) : VoiceWakeSettingsAction
    data class PermissionsResult(val granted: Boolean) : VoiceWakeSettingsAction
    data class PermissionRequestHandled(val requestId: Int) : VoiceWakeSettingsAction
}
