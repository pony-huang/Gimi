package github.ponyhuang.gimi.feature.settings

/** 设置首页状态。 */
class SettingsUiState

sealed interface SettingsAction {
    data object OpenModelService : SettingsAction
    data object OpenDefaultModels : SettingsAction
    data object OpenMcpServers : SettingsAction
    data object OpenPlugins : SettingsAction
    data object OpenSkills : SettingsAction
    data object OpenWorkFiles : SettingsAction
    /** 打开附件工作区管理页。 */
    data object OpenWorkspace : SettingsAction
    data object OpenPermissions : SettingsAction
    data object OpenToolAuthorization : SettingsAction
    data object OpenRecommendations : SettingsAction
    data object OpenMemory : SettingsAction
    /** 在浏览器中打开 GitHub 项目页。 */
    data object OpenProjectPage : SettingsAction
}

sealed interface SettingsEffect {
    data object NavigateToModelService : SettingsEffect
    data object NavigateToDefaultModels : SettingsEffect
    data object NavigateToMcpServers : SettingsEffect
    data object NavigateToPlugins : SettingsEffect
    data object NavigateToSkills : SettingsEffect
    data object NavigateToWorkFiles : SettingsEffect
    /** 跳转附件工作区管理页。 */
    data object NavigateToWorkspace : SettingsEffect
    data object NavigateToPermissions : SettingsEffect
    data object NavigateToToolAuthorization : SettingsEffect
    data object NavigateToRecommendations : SettingsEffect
    data object NavigateToMemory : SettingsEffect
    /** 打开 GitHub 项目页（https://github.com/pony-huang/Gimi）。 */
    data object OpenProjectPage : SettingsEffect
}
