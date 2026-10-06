package github.ponyhuang.gimi.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
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
                    SettingsNavigationItems(
                        items = listOf(
                            SettingsNavigationItem(
                                icon = Icons.Default.SmartToy,
                                title = stringResource(R.string.settings_model_service_title),
                                subtitle = stringResource(R.string.settings_model_service_subtitle),
                                action = SettingsAction.OpenModelService,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Memory,
                                title = stringResource(R.string.settings_local_model_title),
                                subtitle = stringResource(R.string.settings_local_model_subtitle),
                                action = SettingsAction.OpenLocalModels,
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
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_tools)) }
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
                                icon = Icons.Default.Folder,
                                title = stringResource(R.string.settings_work_files_title),
                                subtitle = stringResource(R.string.settings_work_files_subtitle),
                                action = SettingsAction.OpenWorkFiles,
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
                            SettingsNavigationItem(
                                icon = Icons.Default.FolderOpen,
                                title = stringResource(R.string.settings_workspace_title),
                                subtitle = stringResource(R.string.settings_workspace_subtitle),
                                action = SettingsAction.OpenWorkspace,
                            ),
                            SettingsNavigationItem(
                                icon = Icons.Default.Rule,
                                title = stringResource(R.string.settings_tool_authorization_title),
                                subtitle = stringResource(R.string.settings_tool_authorization_subtitle),
                                action = SettingsAction.OpenToolAuthorization,
                            ),
                        ),
                        onAction = onAction,
                        showLastDivider = true,
                    )
                    // Shizuku 是开关型低频入口，固定在分组末尾，避免干扰上方常用项的定位
                    PreferenceListItem(
                        icon = Icons.Default.Phonelink,
                        title = stringResource(R.string.settings_mobile_use_title),
                        subtitle = stringResource(R.string.settings_mobile_use_subtitle),
                        onClick = if (state.mobileUseEnabled && !state.mobileUseUpdating) {
                            { onAction(SettingsAction.OpenMobileUse) }
                        } else null,
                        trailingContent = {
                            val switchDescription = stringResource(R.string.settings_mobile_use_title)
                            Switch(
                                checked = state.mobileUseEnabled,
                                enabled = !state.mobileUseUpdating,
                                onCheckedChange = { onAction(SettingsAction.SetMobileUseEnabled(it)) },
                                modifier = Modifier.semantics { contentDescription = switchDescription },
                            )
                            if (state.mobileUseEnabled) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.settings_group_general)) }
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
                            SettingsNavigationItem(
                                icon = Icons.Default.Security,
                                title = stringResource(R.string.settings_permissions_title),
                                subtitle = stringResource(R.string.settings_permissions_subtitle),
                                action = SettingsAction.OpenPermissions,
                            ),
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
