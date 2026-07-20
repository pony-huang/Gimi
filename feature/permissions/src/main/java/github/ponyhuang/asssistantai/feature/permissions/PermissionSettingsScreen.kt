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
import androidx.compose.ui.unit.dp
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
            item { SettingsSectionTitle(text = "普通权限") }
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
                                "普通权限已全部授权"
                            } else {
                                "一键授权普通权限"
                            },
                        )
                    }
                    if (state.permanentlyDenied.isNotEmpty()) {
                        Text(
                            text = "部分权限已被系统阻止，请前往应用设置手动开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                onAction(PermissionSettingsAction.OpenApplicationSettings)
                            },
                        ) {
                            Text("打开应用设置")
                        }
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = group.kind) {
                    SettingsListItem(
                        icon = group.kind.icon,
                        title = group.title,
                        subtitle = group.subtitle,
                        onClick = if (group.status == PermissionGroupStatus.Granted) {
                            null
                        } else {
                            { onAction(PermissionSettingsAction.RequestGroup(group.kind)) }
                        },
                        trailingContent = {
                            PermissionStatusText(
                                granted = group.status == PermissionGroupStatus.Granted,
                                text = group.status.label,
                            )
                        },
                    )
                }
            }

            item { SettingsSectionTitle(text = "特殊权限") }
            item {
                SettingsListItem(
                    icon = Icons.Default.Tune,
                    title = "修改系统设置",
                    subtitle = "用于调整屏幕亮度和自动息屏时间",
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
                    title = "通知使用权",
                    subtitle = "用于读取并控制其他应用的媒体播放",
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
    text: String = if (granted) "已授权" else "未授权",
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

private val PermissionGroupStatus.label: String
    get() = when (this) {
        PermissionGroupStatus.Granted -> "已授权"
        PermissionGroupStatus.Denied -> "未授权"
        PermissionGroupStatus.PartiallyGranted -> "部分授权"
    }
