package github.ponyhuang.gimi.feature.voicewake

import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState

/** 语音唤醒设置页的完整展示状态。 */
data class VoiceWakeSettingsUiState(
    val voiceState: VoiceWakeState = VoiceWakeState(),
    val configurationReady: Boolean = false,
    val permissionRequestId: Int? = null,
)

sealed interface VoiceWakeSettingsAction {
    /** 切换后台监听开关。 */
    data class ToggleListening(val enabled: Boolean) : VoiceWakeSettingsAction

    /** 选择并准备指定语言的唤醒模型。 */
    data class SelectModel(val modelId: String) : VoiceWakeSettingsAction

    /** 安装或重试指定唤醒模型。 */
    data class InstallModel(val modelId: String) : VoiceWakeSettingsAction

    /** 取消指定唤醒模型的下载或解包。 */
    data class CancelInstall(val modelId: String) : VoiceWakeSettingsAction

    /** 提交系统权限请求结果。 */
    data class PermissionsResult(val granted: Boolean) : VoiceWakeSettingsAction

    /** 标记一次权限请求已由 Route 消费。 */
    data class PermissionRequestHandled(val requestId: Int) : VoiceWakeSettingsAction
}
