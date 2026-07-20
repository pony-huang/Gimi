package github.ponyhuang.asssistantai.feature.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.asssistantai.domain.permissions.model.RuntimeAppPermissions
import github.ponyhuang.asssistantai.domain.permissions.usecase.GetPermissionSnapshotUseCase
import github.ponyhuang.asssistantai.domain.permissions.usecase.RecordPermanentlyDeniedPermissionsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class PermissionSettingsViewModel @Inject constructor(
    private val getSnapshot: GetPermissionSnapshotUseCase,
    private val recordPermanentlyDenied: RecordPermanentlyDeniedPermissionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(getSnapshot().toUiState())
    val uiState = _uiState.asStateFlow()
    private var nextRequestId = 0

    fun onAction(action: PermissionSettingsAction) {
        when (action) {
            PermissionSettingsAction.Refresh -> refresh()
            PermissionSettingsAction.RequestAllRuntimePermissions -> requestRuntimePermissions(
                RuntimeAppPermissions.filterNot { permission -> isGranted(permission) },
            )
            is PermissionSettingsAction.RequestGroup -> requestGroup(action.kind)
            is PermissionSettingsAction.RuntimePermissionsResult -> {
                recordPermanentlyDenied(action.permanentlyDenied)
                refresh()
            }
            PermissionSettingsAction.OpenApplicationSettings ->
                requestSettings(PermissionSettingsDestination.ApplicationDetails)
            PermissionSettingsAction.OpenWriteSettings ->
                requestSettings(PermissionSettingsDestination.WriteSystemSettings)
            PermissionSettingsAction.OpenNotificationAccess ->
                requestSettings(PermissionSettingsDestination.NotificationListener)
            is PermissionSettingsAction.RuntimeRequestHandled -> _uiState.update {
                if (it.runtimeRequest?.id == action.requestId) {
                    it.copy(runtimeRequest = null)
                } else {
                    it
                }
            }
            is PermissionSettingsAction.SettingsRequestHandled -> _uiState.update {
                if (it.settingsRequest?.id == action.requestId) {
                    it.copy(settingsRequest = null)
                } else {
                    it
                }
            }
        }
    }

    private fun refresh() {
        val snapshot = getSnapshot()
        _uiState.update { current ->
            snapshot.toUiState().copy(
                runtimeRequest = current.runtimeRequest,
                settingsRequest = current.settingsRequest,
            )
        }
    }

    private fun requestGroup(kind: PermissionGroupKind) {
        val state = _uiState.value
        val group = state.groups.firstOrNull { it.kind == kind } ?: return
        val missing = group.permissions.filterNot(::isGranted)
        if (missing.isEmpty()) return
        if (missing.any { it in state.permanentlyDenied }) {
            requestSettings(PermissionSettingsDestination.ApplicationDetails)
        } else {
            requestRuntimePermissions(missing)
        }
    }

    private fun requestRuntimePermissions(permissions: List<AppPermission>) {
        if (permissions.isEmpty()) return
        _uiState.update {
            it.copy(
                runtimeRequest = RuntimePermissionRequest(
                    id = ++nextRequestId,
                    permissions = permissions,
                ),
            )
        }
    }

    private fun requestSettings(destination: PermissionSettingsDestination) {
        _uiState.update {
            it.copy(
                settingsRequest = PermissionSettingsRequest(
                    id = ++nextRequestId,
                    destination = destination,
                ),
            )
        }
    }

    private fun isGranted(permission: AppPermission): Boolean = permission in _uiState.value.granted
}

private fun PermissionSnapshot.toUiState(): PermissionSettingsUiState {
    val groups = PermissionGroupDefinition.entries.map { definition ->
        val grantedCount = definition.permissions.count(::isGranted)
        PermissionGroupUiModel(
            kind = definition.kind,
            title = definition.title,
            subtitle = definition.subtitle,
            permissions = definition.permissions,
            status = when (grantedCount) {
                definition.permissions.size -> PermissionGroupStatus.Granted
                0 -> PermissionGroupStatus.Denied
                else -> PermissionGroupStatus.PartiallyGranted
            },
        )
    }
    return PermissionSettingsUiState(
        groups = groups,
        granted = granted,
        permanentlyDenied = permanentlyDenied,
        allRuntimeGranted = RuntimeAppPermissions.all(::isGranted),
        writeSettingsGranted = isGranted(AppPermission.WriteSystemSettings),
        notificationAccessGranted = isGranted(AppPermission.NotificationListener),
    )
}

private enum class PermissionGroupDefinition(
    val kind: PermissionGroupKind,
    val title: String,
    val subtitle: String,
    val permissions: List<AppPermission>,
) {
    Location(
        PermissionGroupKind.Location,
        "位置",
        "允许助手获取当前位置并执行位置相关任务",
        listOf(AppPermission.FineLocation, AppPermission.CoarseLocation),
    ),
    Calendar(
        PermissionGroupKind.Calendar,
        "日历",
        "允许助手读取、创建和更新日历事件",
        listOf(AppPermission.ReadCalendar, AppPermission.WriteCalendar),
    ),
    Media(
        PermissionGroupKind.Media,
        "媒体文件",
        "允许助手搜索共享图片、视频和音频",
        listOf(
            AppPermission.ReadMediaImages,
            AppPermission.ReadMediaVideo,
            AppPermission.ReadMediaAudio,
        ),
    ),
    Microphone(
        PermissionGroupKind.Microphone,
        "麦克风",
        "用于语音输入和蓝牙语音唤醒",
        listOf(AppPermission.RecordAudio),
    ),
    Bluetooth(
        PermissionGroupKind.Bluetooth,
        "蓝牙设备",
        "用于连接耳机并执行语音唤醒任务",
        listOf(AppPermission.BluetoothConnect),
    ),
    Notifications(
        PermissionGroupKind.Notifications,
        "通知",
        "用于显示后台语音任务的运行状态",
        listOf(AppPermission.PostNotifications),
    ),
}
