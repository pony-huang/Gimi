package github.ponyhuang.gimi.feature.settings

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
}
