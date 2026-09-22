package github.ponyhuang.gimi.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
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
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_model)) }
            item {
                PreferenceGroupCard {
                    PreferenceNavigationCard(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.settings_model_service_title),
                        subtitle = stringResource(R.string.settings_model_service_subtitle),
                        onClick = { onAction(SettingsAction.OpenModelService) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.settings_default_model_title),
                        subtitle = stringResource(R.string.settings_default_model_subtitle),
                        onClick = { onAction(SettingsAction.OpenDefaultModels) },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_tools)) }
            item {
                PreferenceGroupCard {
                    PreferenceNavigationCard(
                        icon = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
                        title = stringResource(R.string.settings_mcp_title),
                        subtitle = stringResource(R.string.settings_mcp_subtitle),
                        onClick = { onAction(SettingsAction.OpenMcpServers) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Extension,
                        title = stringResource(R.string.settings_plugins_title),
                        subtitle = stringResource(R.string.settings_plugins_subtitle),
                        onClick = { onAction(SettingsAction.OpenPlugins) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Rule,
                        title = stringResource(R.string.settings_tool_authorization_title),
                        subtitle = stringResource(R.string.settings_tool_authorization_subtitle),
                        onClick = { onAction(SettingsAction.OpenToolAuthorization) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.School,
                        title = stringResource(R.string.settings_skills_title),
                        subtitle = stringResource(R.string.settings_skills_subtitle),
                        onClick = { onAction(SettingsAction.OpenSkills) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.settings_work_files_title),
                        subtitle = stringResource(R.string.settings_work_files_subtitle),
                        onClick = { onAction(SettingsAction.OpenWorkFiles) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.FolderOpen,
                        title = stringResource(R.string.settings_workspace_title),
                        subtitle = stringResource(R.string.settings_workspace_subtitle),
                        onClick = { onAction(SettingsAction.OpenWorkspace) },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_general)) }
            item {
                PreferenceGroupCard {
                    PreferenceNavigationCard(
                        icon = Icons.Default.Psychology,
                        title = stringResource(R.string.settings_memory_title),
                        subtitle = stringResource(R.string.settings_memory_subtitle),
                        onClick = { onAction(SettingsAction.OpenMemory) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.settings_recommendations_title),
                        subtitle = stringResource(R.string.settings_recommendations_subtitle),
                        onClick = { onAction(SettingsAction.OpenRecommendations) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.settings_permissions_title),
                        subtitle = stringResource(R.string.settings_permissions_subtitle),
                        onClick = { onAction(SettingsAction.OpenPermissions) },
                        showDivider = true,
                    )
                    PreferenceNavigationCard(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_about_title),
                        subtitle = stringResource(R.string.settings_about_subtitle, appVersionName),
                        onClick = { onAction(SettingsAction.OpenAbout) },
                    )
                }
            }
        }
    }
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
                state = SettingsUiState(),
                appVersionName = "1.0",
                onAction = {},
                modifier = modifier,
            )
        }
    }
}
