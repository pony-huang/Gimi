package github.ponyhuang.gimi.feature.settings

/**
 * 设置首页状态。
 * @property mobileUseEnabled 用户是否开启 Agent 后台操作。
 * @property mobileUseUpdating 是否正在保存开关和清理后台任务。
 */
data class SettingsUiState(
    val mobileUseEnabled: Boolean = false,
    val mobileUseUpdating: Boolean = false,
)

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
    /** 进入已开启的 Shizuku 授权页。 */
    data object OpenMobileUse : SettingsAction
    /** 设置后台操作开关，enabled 为用户选择的状态。 */
    data class SetMobileUseEnabled(val enabled: Boolean) : SettingsAction
    data object OpenRecommendations : SettingsAction
    data object OpenMemory : SettingsAction
    /** 进入「关于」页（内含检查更新、项目主页等）。 */
    data object OpenAbout : SettingsAction
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
    data object NavigateToMobileUse : SettingsEffect
    data object NavigateToRecommendations : SettingsEffect
    data object NavigateToMemory : SettingsEffect
    /** 进入「关于」页。 */
    data object NavigateToAbout : SettingsEffect
}
