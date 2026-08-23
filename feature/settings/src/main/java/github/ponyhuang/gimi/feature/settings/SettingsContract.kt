package github.ponyhuang.gimi.feature.settings

/**
 * 设置首页状态。
 *
 * @property showToolActivity 是否显示聊天工具过程。
 */
data class SettingsUiState(
    val showToolActivity: Boolean = false,
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
    /** 打开 GitHub 项目页（https://github.com/pony-huang/Gimi）。 */
    data object OpenProjectPage : SettingsEffect
}
