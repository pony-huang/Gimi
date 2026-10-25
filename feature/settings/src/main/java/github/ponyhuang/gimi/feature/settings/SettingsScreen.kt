package github.ponyhuang.gimi.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phonelink
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
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun SettingsScreen(
    appVersionName: String,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_model)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.SmartToy,
                                title = stringResource(R.string.settings_model_service_title),
                                subtitle = stringResource(R.string.settings_model_service_subtitle),
                                action = SettingsAction.OpenModelService,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Tune,
                                title = stringResource(R.string.settings_default_model_title),
                                subtitle = stringResource(R.string.settings_default_model_subtitle),
                                action = SettingsAction.OpenDefaultModels,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_extensions)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
                                title = stringResource(R.string.settings_mcp_title),
                                subtitle = stringResource(R.string.settings_mcp_subtitle),
                                action = SettingsAction.OpenMcpServers,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Hub,
                                title = stringResource(R.string.settings_remote_agents_title),
                                subtitle = stringResource(R.string.settings_remote_agents_subtitle),
                                action = SettingsAction.OpenRemoteAgents,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Apps,
                                title = stringResource(R.string.settings_appfunction_title),
                                subtitle = stringResource(R.string.settings_appfunction_subtitle),
                                action = SettingsAction.OpenAppFunctions,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Code,
                                title = stringResource(R.string.settings_sandbox_title),
                                subtitle = stringResource(R.string.settings_sandbox_subtitle),
                                action = SettingsAction.OpenSandbox,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.School,
                                title = stringResource(R.string.settings_skills_title),
                                subtitle = stringResource(R.string.settings_skills_subtitle),
                                action = SettingsAction.OpenSkills,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Extension,
                                title = stringResource(R.string.settings_plugins_title),
                                subtitle = stringResource(R.string.settings_plugins_subtitle),
                                action = SettingsAction.OpenPlugins,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_files)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.FolderOpen,
                                title = stringResource(R.string.settings_workspace_title),
                                subtitle = stringResource(R.string.settings_workspace_subtitle),
                                action = SettingsAction.OpenWorkspace,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_assistant)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.Psychology,
                                title = stringResource(R.string.settings_memory_title),
                                subtitle = stringResource(R.string.settings_memory_subtitle),
                                action = SettingsAction.OpenMemory,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.AutoAwesome,
                                title = stringResource(R.string.settings_recommendations_title),
                                subtitle = stringResource(R.string.settings_recommendations_subtitle),
                                action = SettingsAction.OpenRecommendations,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_permissions)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.Rule,
                                title = stringResource(R.string.settings_tool_authorization_title),
                                subtitle = stringResource(R.string.settings_tool_authorization_subtitle),
                                action = SettingsAction.OpenToolAuthorization,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Security,
                                title = stringResource(R.string.settings_permissions_title),
                                subtitle = stringResource(R.string.settings_permissions_subtitle),
                                action = SettingsAction.OpenPermissions,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Phonelink,
                                title = stringResource(R.string.settings_mobile_use_title),
                                subtitle = stringResource(R.string.settings_mobile_use_subtitle),
                                action = SettingsAction.OpenMobileUse,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_about)) }
            item {
                PreferenceGroupCard {
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.Info,
                                title = stringResource(R.string.settings_about_title),
                                subtitle = stringResource(R.string.settings_about_subtitle, appVersionName),
                                action = SettingsAction.OpenAbout,
                            ),
                        ),
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

/** 单个首页导航入口的显示内容及点击意图，不持有页面状态或导航控制器。 */
private data class SettingsNavigationItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val action: SettingsAction,
)

/** 分隔线由列表位置统一决定；组尾还有其他类型的行时可保留末行分隔线。 */
@Composable
private fun ColumnScope.SettingsNavigationItems(
    items: List<SettingsNavigationItem>,
    onAction: (SettingsAction) -> Unit,
    showLastDivider: Boolean = false,
) {
    items.forEachIndexed { index, item ->
        PreferenceListItem(
            icon = item.icon,
            title = item.title,
            subtitle = item.subtitle,
            onClick = { onAction(item.action) },
            showDivider = index < items.lastIndex || showLastDivider,
        )
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Narrow large text", widthDp = 320, heightDp = 780, fontScale = 1.3f)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
private annotation class SettingsFormFactorPreviews

@SettingsFormFactorPreviews
@Composable
private fun SettingsHomePreview() {
    AsssistantaiTheme {
        PreferenceScaffold(style = PreferenceStyle.Compact, title = stringResource(R.string.settings_title), onBack = {}) { modifier ->
            SettingsScreen(
                appVersionName = "1.0",
                onAction = {},
                modifier = modifier,
            )
        }
    }
}
