package github.ponyhuang.gimi.feature.permissions

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.feature.permissions.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun PermissionSettingsScreen(
    state: PermissionSettingsUiState,
    onAction: (PermissionSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(text = stringResource(R.string.permissions_section_regular)) }
            item {
                // 常规权限按组单独申请，永久拒绝时通过对应项目打开应用设置。
                PreferenceGroupCard {
                    state.groups.forEachIndexed { index, group ->
                        PreferenceListItem(
                            icon = group.kind.icon,
                            title = stringResource(group.titleRes),
                            subtitle = stringResource(group.subtitleRes),
                            showDivider = index < state.groups.lastIndex,
                            onClick = if (group.status == PermissionGroupStatus.Granted) {
                                null
                            } else {
                                { onAction(PermissionSettingsAction.RequestGroup(group.kind)) }
                            },
                            trailingContent = {
                                PermissionStatusText(
                                    granted = group.status == PermissionGroupStatus.Granted,
                                    text = group.status.label(),
                                )
                            },
                        )
                    }
                }
            }

            item { PreferenceSectionTitle(text = stringResource(R.string.permissions_section_special)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Layers,
                        title = stringResource(R.string.permissions_special_overlay_title),
                        subtitle = stringResource(R.string.permissions_special_overlay_subtitle),
                        showDivider = true,
                        onClick = if (state.overlayGranted) null else {
                            { onAction(PermissionSettingsAction.OpenOverlaySettings) }
                        },
                        trailingContent = { PermissionStatusText(granted = state.overlayGranted) },
                    )
                    PreferenceListItem(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.permissions_special_modify_settings_title),
                        subtitle = stringResource(R.string.permissions_special_modify_settings_subtitle),
                        showDivider = true,
                        onClick = if (state.writeSettingsGranted) {
                            null
                        } else {
                            { onAction(PermissionSettingsAction.OpenWriteSettings) }
                        },
                        trailingContent = {
                            PermissionStatusText(granted = state.writeSettingsGranted)
                        },
                    )
                    PreferenceListItem(
                        icon = Icons.Default.NotificationsActive,
                        title = stringResource(R.string.permissions_special_notification_access_title),
                        subtitle = stringResource(R.string.permissions_special_notification_access_subtitle),
                        showDivider = true,
                        onClick = if (state.notificationAccessGranted) {
                            null
                        } else {
                            { onAction(PermissionSettingsAction.OpenNotificationAccess) }
                        },
                        trailingContent = {
                            PermissionStatusText(granted = state.notificationAccessGranted)
                        },
                    )
                    PreferenceListItem(
                        icon = Icons.Default.Apps,
                        title = stringResource(R.string.permissions_special_usage_access_title),
                        subtitle = stringResource(R.string.permissions_special_usage_access_subtitle),
                        onClick = if (state.usageAccessGranted) {
                            null
                        } else {
                            { onAction(PermissionSettingsAction.OpenUsageAccess) }
                        },
                        trailingContent = {
                            PermissionStatusText(granted = state.usageAccessGranted)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusText(
    granted: Boolean,
    text: String = if (granted) {
        stringResource(R.string.permissions_status_granted)
    } else {
        stringResource(R.string.permissions_status_not_granted)
    },
) {
    // 已授权是静止状态，用弱化色降噪；未授权需要用户行动，才用强调色吸引点击。
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (granted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
}

private val PermissionGroupKind.icon: ImageVector
    get() = when (this) {
        PermissionGroupKind.Location -> Icons.Default.LocationOn
        PermissionGroupKind.Calendar -> Icons.Default.CalendarMonth
        PermissionGroupKind.Media -> Icons.Default.Folder
        PermissionGroupKind.Microphone -> Icons.Default.Mic
        PermissionGroupKind.Notifications -> Icons.Default.Notifications
    }

@Composable
private fun PermissionGroupStatus.label(): String = when (this) {
    PermissionGroupStatus.Granted -> stringResource(R.string.permissions_status_granted)
    PermissionGroupStatus.Denied -> stringResource(R.string.permissions_status_not_granted)
    PermissionGroupStatus.PartiallyGranted -> stringResource(R.string.permissions_status_partially_granted)
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionSettingsScreenDeniedPreview() {
    AsssistantaiTheme {
        PermissionSettingsScreen(
            state = PermissionSettingsUiState(
                groups = listOf(
                    PermissionGroupUiModel(
                        kind = PermissionGroupKind.Microphone,
                        titleRes = R.string.permission_name_microphone,
                        subtitleRes = R.string.permission_desc_microphone,
                        permissions = listOf(AppPermission.RecordAudio),
                        status = PermissionGroupStatus.Denied,
                    ),
                    PermissionGroupUiModel(
                        kind = PermissionGroupKind.Location,
                        titleRes = R.string.permission_name_location,
                        subtitleRes = R.string.permission_desc_location,
                        permissions = listOf(AppPermission.CoarseLocation, AppPermission.FineLocation),
                        status = PermissionGroupStatus.PartiallyGranted,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionSettingsScreenGrantedPreview() {
    AsssistantaiTheme {
        PermissionSettingsScreen(
            state = PermissionSettingsUiState(
                groups = listOf(
                    PermissionGroupUiModel(
                        kind = PermissionGroupKind.Notifications,
                        titleRes = R.string.permission_name_notifications,
                        subtitleRes = R.string.permission_desc_notifications,
                        permissions = listOf(AppPermission.PostNotifications),
                        status = PermissionGroupStatus.Granted,
                    ),
                ),
                overlayGranted = true,
                writeSettingsGranted = true,
                notificationAccessGranted = true,
                permanentlyDenied = setOf(AppPermission.RecordAudio),
            ),
            onAction = {},
        )
    }
}
