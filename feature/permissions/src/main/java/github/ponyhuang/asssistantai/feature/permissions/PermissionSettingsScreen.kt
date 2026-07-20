package github.ponyhuang.asssistantai.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.feature.permissions.R
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun PermissionSettingsScreen(
    state: PermissionSettingsUiState,
    onAction: (PermissionSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { SettingsSectionTitle(text = stringResource(R.string.permissions_section_regular)) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            onAction(PermissionSettingsAction.RequestAllRuntimePermissions)
                        },
                        enabled = !state.allRuntimeGranted,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.allRuntimeGranted) {
                                stringResource(R.string.permissions_regular_all_granted)
                            } else {
                                stringResource(R.string.permissions_grant_all)
                            },
                        )
                    }
                    if (state.permanentlyDenied.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.permissions_partial_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                onAction(PermissionSettingsAction.OpenApplicationSettings)
                            },
                        ) {
                            Text(stringResource(R.string.permissions_open_settings))
                        }
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = group.kind) {
                    SettingsListItem(
                        icon = group.kind.icon,
                        title = stringResource(group.titleRes),
                        subtitle = stringResource(group.subtitleRes),
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

            item { SettingsSectionTitle(text = stringResource(R.string.permissions_section_special)) }
            item {
                SettingsListItem(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.permissions_special_modify_settings_title),
                    subtitle = stringResource(R.string.permissions_special_modify_settings_subtitle),
                    onClick = if (state.writeSettingsGranted) {
                        null
                    } else {
                        { onAction(PermissionSettingsAction.OpenWriteSettings) }
                    },
                    trailingContent = {
                        PermissionStatusText(granted = state.writeSettingsGranted)
                    },
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.permissions_special_notification_access_title),
                    subtitle = stringResource(R.string.permissions_special_notification_access_subtitle),
                    onClick = if (state.notificationAccessGranted) {
                        null
                    } else {
                        { onAction(PermissionSettingsAction.OpenNotificationAccess) }
                    },
                    trailingContent = {
                        PermissionStatusText(granted = state.notificationAccessGranted)
                    },
                )
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
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private val PermissionGroupKind.icon: ImageVector
    get() = when (this) {
        PermissionGroupKind.Location -> Icons.Default.LocationOn
        PermissionGroupKind.Calendar -> Icons.Default.CalendarMonth
        PermissionGroupKind.Media -> Icons.Default.Folder
        PermissionGroupKind.Microphone -> Icons.Default.Mic
        PermissionGroupKind.Bluetooth -> Icons.Default.Bluetooth
        PermissionGroupKind.Notifications -> Icons.Default.Notifications
    }

@Composable
private fun PermissionGroupStatus.label(): String = when (this) {
    PermissionGroupStatus.Granted -> stringResource(R.string.permissions_status_granted)
    PermissionGroupStatus.Denied -> stringResource(R.string.permissions_status_not_granted)
    PermissionGroupStatus.PartiallyGranted -> stringResource(R.string.permissions_status_partially_granted)
}
