package github.ponyhuang.asssistantai.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class RuntimePermissionGroup(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val permissions: List<String>,
)

/** Central place to grant capabilities used by chat tools before starting a task. */
@Composable
fun PermissionSettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember {
        context.getSharedPreferences(PERMISSION_PREFERENCES, Context.MODE_PRIVATE)
    }
    val permissionGroups = remember { runtimePermissionGroups() }
    var refreshToken by remember { mutableIntStateOf(0) }
    var permanentlyDenied by remember {
        mutableStateOf(preferences.getStringSet(PERMANENTLY_DENIED_KEY, emptySet()).orEmpty())
    }

    fun refreshPermissionState() {
        permanentlyDenied = permanentlyDenied.filterTo(mutableSetOf()) { permission ->
            !context.hasPermission(permission)
        }
        preferences.edit { putStringSet(PERMANENTLY_DENIED_KEY, permanentlyDenied) }
        refreshToken++
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissionState()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val newlyBlocked = grants
            .filterValues { granted -> !granted }
            .keys
            .filterTo(mutableSetOf()) { permission ->
                activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    permission,
                )
            }
        permanentlyDenied = (permanentlyDenied + newlyBlocked).filterTo(mutableSetOf()) {
            !context.hasPermission(it)
        }
        preferences.edit { putStringSet(PERMANENTLY_DENIED_KEY, permanentlyDenied) }
        refreshToken++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val missingPermissions = remember(refreshToken, permissionGroups) {
        permissionGroups.flatMap { it.permissions }.distinct().filterNot(context::hasPermission)
    }
    val canWriteSettings = remember(refreshToken) { Settings.System.canWrite(context) }
    val hasNotificationAccess = remember(refreshToken) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val openApplicationSettings = {
        settingsLauncher.launch(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }

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
                        onClick = { permissionLauncher.launch(missingPermissions.toTypedArray()) },
                        enabled = missingPermissions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (missingPermissions.isEmpty()) {
                                "普通权限已全部授权"
                            } else {
                                "一键授权普通权限"
                            },
                        )
                    }
                    if (permanentlyDenied.isNotEmpty()) {
                        Text(
                            text = "部分权限已被系统阻止，请前往应用设置手动开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = openApplicationSettings) {
                            Text("打开应用设置")
                        }
                    }
                }
            }
            permissionGroups.forEach { group ->
                item(key = group.title) {
                    val grantedCount = group.permissions.count(context::hasPermission)
                    val missing = group.permissions.filterNot(context::hasPermission)
                    val status = when (grantedCount) {
                        group.permissions.size -> "已授权"
                        0 -> "未授权"
                        else -> "部分授权"
                    }
                    SettingsListItem(
                        icon = group.icon,
                        title = group.title,
                        subtitle = group.subtitle,
                        onClick = if (missing.isEmpty()) {
                            null
                        } else if (missing.any { it in permanentlyDenied }) {
                            openApplicationSettings
                        } else {
                            { permissionLauncher.launch(missing.toTypedArray()) }
                        },
                        trailingContent = {
                            PermissionStatusText(granted = missing.isEmpty(), text = status)
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
                    onClick = if (canWriteSettings) null else {{
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    }},
                    trailingContent = {
                        PermissionStatusText(granted = canWriteSettings)
                    },
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "通知使用权",
                    subtitle = "用于读取并控制其他应用的媒体播放",
                    onClick = if (hasNotificationAccess) null else {{
                        settingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }},
                    trailingContent = {
                        PermissionStatusText(granted = hasNotificationAccess)
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

private fun runtimePermissionGroups(): List<RuntimePermissionGroup> = listOf(
    RuntimePermissionGroup(
        title = "位置",
        subtitle = "允许助手获取当前位置并执行位置相关任务",
        icon = Icons.Default.LocationOn,
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    ),
    RuntimePermissionGroup(
        title = "日历",
        subtitle = "允许助手读取、创建和更新日历事件",
        icon = Icons.Default.CalendarMonth,
        permissions = listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        ),
    ),
    RuntimePermissionGroup(
        title = "媒体文件",
        subtitle = "允许助手搜索共享图片、视频和音频",
        icon = Icons.Default.Folder,
        permissions = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ),
    ),
    RuntimePermissionGroup(
        title = "麦克风",
        subtitle = "用于语音输入和蓝牙语音唤醒",
        icon = Icons.Default.Mic,
        permissions = listOf(Manifest.permission.RECORD_AUDIO),
    ),
    RuntimePermissionGroup(
        title = "蓝牙设备",
        subtitle = "用于连接耳机并执行语音唤醒任务",
        icon = Icons.Default.Bluetooth,
        permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
    ),
    RuntimePermissionGroup(
        title = "通知",
        subtitle = "用于显示后台语音任务的运行状态",
        icon = Icons.Default.Notifications,
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
    ),
)

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PERMISSION_PREFERENCES = "permission_settings"
private const val PERMANENTLY_DENIED_KEY = "permanently_denied"
