package github.ponyhuang.gimi.feature.settings

/**
 * 设置首页状态。
 *
 * @property showToolActivity 是否显示聊天工具过程。
 * @property showAppFunctions 当前设备是否具备 AppFunctions 运行时能力。
 */
data class SettingsUiState(
    val showToolActivity: Boolean = false,
    val showAppFunctions: Boolean = false,
)

sealed interface SettingsAction {
    data object OpenModelService : SettingsAction
    data object OpenDefaultModels : SettingsAction
    data object OpenVoiceWake : SettingsAction
    data object OpenMcpServers : SettingsAction
    data object OpenSkills : SettingsAction
    data object OpenWorkFiles : SettingsAction
    data object OpenPermissions : SettingsAction
    data object OpenToolAuthorization : SettingsAction
    /** 打开 AppFunctions 尝鲜设置。 */
    data object OpenAppFunctions : SettingsAction

    /** 在浏览器中打开 GitHub 项目页。 */
    data object OpenProjectPage : SettingsAction
    data class SetToolActivityVisible(val visible: Boolean) : SettingsAction
}

sealed interface SettingsEffect {
    data object NavigateToModelService : SettingsEffect
    data object NavigateToDefaultModels : SettingsEffect
    data object NavigateToVoiceWake : SettingsEffect
    data object NavigateToMcpServers : SettingsEffect
    data object NavigateToSkills : SettingsEffect
    data object NavigateToWorkFiles : SettingsEffect
    data object NavigateToPermissions : SettingsEffect
    data object NavigateToToolAuthorization : SettingsEffect
    /** 导航到 AppFunctions 尝鲜设置。 */
    data object NavigateToAppFunctions : SettingsEffect

    /** 打开 GitHub 项目页（https://github.com/pony-huang/Gimi）。 */
    data object OpenProjectPage : SettingsEffect
}
