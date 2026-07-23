package github.ponyhuang.asssistantai.feature.assistant

/** 助理设置页界面状态。 */
data class AssistantSettingsUiState(
    /** ROM 是否提供默认助理角色。 */
    val roleAvailable: Boolean = true,
    /** 应用是否持有默认助理角色。 */
    val isDefaultAssistant: Boolean = false,
    /** 麦克风运行时权限是否已授予。 */
    val microphoneGranted: Boolean = false,
    /** 磁贴添加请求是否已发送（用于一次性提示）。 */
    val tileAddRequested: Boolean = false,
)

sealed interface AssistantSettingsAction {
    /** 回到页面/权限或角色变化后刷新状态。 */
    data object RefreshStatus : AssistantSettingsAction

    data class MicrophonePermissionResult(val granted: Boolean) : AssistantSettingsAction

    /** 磁贴添加请求已发出。 */
    data object TileAddRequested : AssistantSettingsAction
}
