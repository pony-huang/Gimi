package github.ponyhuang.asssistantai.feature.assistant

/** 助理设置页界面状态。 */
data class AssistantSettingsUiState(
    /** 麦克风运行时权限是否已授予。 */
    val microphoneGranted: Boolean = false,
    /** 磁贴添加请求是否已发送（用于一次性提示）。 */
    val tileAddRequested: Boolean = false,
)

sealed interface AssistantSettingsAction {
    data class MicrophonePermissionResult(val granted: Boolean) : AssistantSettingsAction

    /** 磁贴添加请求已发出。 */
    data object TileAddRequested : AssistantSettingsAction
}
