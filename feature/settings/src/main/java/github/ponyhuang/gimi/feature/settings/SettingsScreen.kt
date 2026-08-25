package github.ponyhuang.gimi.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.feature.settings.update.UpdateAction
import github.ponyhuang.gimi.feature.settings.update.UpdateUiState
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceNavigationCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    appVersionName: String,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    updateState: UpdateUiState = UpdateUiState(),
    onUpdateAction: (UpdateAction) -> Unit = {},
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_model)) }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_model_service_title),
                    subtitle = stringResource(R.string.settings_model_service_subtitle),
                    onClick = { onAction(SettingsAction.OpenModelService) },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.settings_default_model_title),
                    subtitle = stringResource(R.string.settings_default_model_subtitle),
                    onClick = { onAction(SettingsAction.OpenDefaultModels) },
                )
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_voice)) }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.settings_voice_wake_title),
                    subtitle = stringResource(R.string.settings_voice_wake_subtitle),
                    onClick = { onAction(SettingsAction.OpenVoiceWake) },
                )
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_tools)) }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.settings_mcp_title),
                    subtitle = stringResource(R.string.settings_mcp_subtitle),
                    onClick = { onAction(SettingsAction.OpenMcpServers) },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.settings_plugins_title),
                    subtitle = stringResource(R.string.settings_plugins_subtitle),
                    onClick = { onAction(SettingsAction.OpenPlugins) },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.settings_tool_authorization_title),
                    subtitle = stringResource(R.string.settings_tool_authorization_subtitle),
                    onClick = { onAction(SettingsAction.OpenToolAuthorization) },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.settings_skills_title),
                    subtitle = stringResource(R.string.settings_skills_subtitle),
                    onClick = { onAction(SettingsAction.OpenSkills) },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.settings_work_files_title),
                    subtitle = stringResource(R.string.settings_work_files_subtitle),
                    onClick = { onAction(SettingsAction.OpenWorkFiles) },
                )
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_general)) }
            item {
                PreferenceListItem(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.settings_chat_display_title),
                    subtitle = stringResource(R.string.settings_chat_display_subtitle),
                    trailingContent = {
                        Switch(
                            checked = state.showToolActivity,
                            onCheckedChange = {
                                onAction(SettingsAction.SetToolActivityVisible(it))
                            },
                        )
                    },
                )
            }
            item {
                PreferenceNavigationCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_permissions_title),
                    subtitle = stringResource(R.string.settings_permissions_subtitle),
                    onClick = { onAction(SettingsAction.OpenPermissions) },
                )
            }
            item {
                PreferenceListItem(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.settings_update_title),
                    subtitle = updateSubtitle(updateState),
                    onClick = { onUpdateAction(UpdateAction.CheckNow) },
                )
            }
            item {
                PreferenceListItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle, appVersionName),
                    onClick = { onAction(SettingsAction.OpenProjectPage) },
                )
            }
        }
    }
}

@Composable
private fun updateSubtitle(updateState: UpdateUiState): String = when (val status = updateState.status) {
    is AppUpdateState.Checking -> stringResource(R.string.update_checking)
    is AppUpdateState.Available ->
        stringResource(R.string.update_available_subtitle, status.info.tagName)
    is AppUpdateState.Downloading ->
        stringResource(R.string.update_downloading_subtitle, (status.progress * 100).toInt())
    is AppUpdateState.Downloaded ->
        stringResource(R.string.update_downloaded_subtitle, status.info.tagName)
    else -> stringResource(R.string.update_subtitle_current, updateState.currentVersionName)
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
private annotation class SettingsFormFactorPreviews

@SettingsFormFactorPreviews
@Composable
private fun SettingsHomePreview() {
    AsssistantaiTheme {
        PreferenceScaffold(title = stringResource(R.string.settings_title), onBack = {}) { modifier ->
            SettingsScreen(
                state = SettingsUiState(showToolActivity = true),
                appVersionName = "1.0",
                onAction = {},
                modifier = modifier,
            )
        }
    }
}
