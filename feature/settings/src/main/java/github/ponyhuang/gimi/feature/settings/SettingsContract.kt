package github.ponyhuang.gimi.feature.settings

sealed interface SettingsAction {
    /** 打开语音厂商配置列表。 */
    data object OpenModelService : SettingsAction
    data object OpenDefaultModels : SettingsAction
    data object OpenMcpServers : SettingsAction
    /** 打开远程 A2A agent 管理页。 */
    data object OpenRemoteAgents : SettingsAction
    /** 打开 AppFunction 应用列表。 */
    data object OpenAppFunctions : SettingsAction
    /** 打开沙盒连接与执行环境配置页。 */
    data object OpenSandbox : SettingsAction
    /** 打开 Shizuku（设备控制）连接与授权状态页。 */
    data object OpenMobileUse : SettingsAction
    data object OpenPlugins : SettingsAction
    data object OpenSkills : SettingsAction
    /** 打开附件工作区管理页。 */
    data object OpenWorkspace : SettingsAction
    data object OpenPermissions : SettingsAction
    data object OpenRecommendations : SettingsAction
    data object OpenMemory : SettingsAction
    /** 进入「关于」页（内含检查更新、项目主页等）。 */
    data object OpenAbout : SettingsAction
}

sealed interface SettingsEffect {
    /** 进入语音厂商配置列表。 */
    data object NavigateToModelService : SettingsEffect
    data object NavigateToDefaultModels : SettingsEffect
    data object NavigateToMcpServers : SettingsEffect
    /** 进入远程 A2A agent 管理页。 */
    data object NavigateToRemoteAgents : SettingsEffect
    /** 进入 AppFunction 拥有的应用列表。 */
    data object NavigateToAppFunctions : SettingsEffect
    /** 进入沙盒功能拥有的设置目的地。 */
    data object NavigateToSandbox : SettingsEffect
    /** 进入设备控制（Shizuku）功能拥有的设置目的地。 */
    data object NavigateToMobileUse : SettingsEffect
    data object NavigateToPlugins : SettingsEffect
    data object NavigateToSkills : SettingsEffect
    /** 跳转附件工作区管理页。 */
    data object NavigateToWorkspace : SettingsEffect
    data object NavigateToPermissions : SettingsEffect
    data object NavigateToRecommendations : SettingsEffect
    data object NavigateToMemory : SettingsEffect
    /** 进入「关于」页。 */
    data object NavigateToAbout : SettingsEffect
}
