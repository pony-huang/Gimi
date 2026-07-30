package github.ponyhuang.gimi.feature.permissions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.gimi.domain.permissions.model.RuntimeAppPermissions
import github.ponyhuang.gimi.domain.permissions.usecase.GetPermissionSnapshotUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.RecordPermanentlyDeniedPermissionsUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.RecordRequestedPermissionsUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.WasPermissionRequestedUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class PermissionSettingsViewModel @Inject constructor(
    private val getSnapshot: GetPermissionSnapshotUseCase,
    private val recordPermanentlyDenied: RecordPermanentlyDeniedPermissionsUseCase,
    private val wasPermissionRequested: WasPermissionRequestedUseCase,
    private val recordRequestedPermissions: RecordRequestedPermissionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(getSnapshot().toUiState())
    val uiState = _uiState.asStateFlow()
    private var nextRequestId = 0

    /**
     * 已发起、等待系统权限框结果的运行时请求。Route 的消费动作会把它从 UiState 清掉，
     * 但 launcher 回调仍需其中的 permissions / previouslyRequested 来判定永久拒绝，
     * 因此由 ViewModel 持有到结果返回；不持有任何 Activity/Context。
     */
    var pendingRuntimeRequest: RuntimePermissionRequest? = null
        private set

    fun onAction(action: PermissionSettingsAction) {
        when (action) {
            PermissionSettingsAction.Refresh -> refresh()
            PermissionSettingsAction.RequestAllRuntimePermissions -> requestRuntimePermissions(
                RuntimeAppPermissions.filterNot { permission -> isGranted(permission) },
            )
            is PermissionSettingsAction.RequestGroup -> requestGroup(action.kind)
            is PermissionSettingsAction.RuntimePermissionsResult -> {
                pendingRuntimeRequest = null
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
        val previouslyRequested = permissions
            .filterTo(mutableSetOf(), wasPermissionRequested::invoke)
        val request = RuntimePermissionRequest(
            id = ++nextRequestId,
            permissions = permissions,
            previouslyRequested = previouslyRequested,
        )
        pendingRuntimeRequest = request
        _uiState.update { it.copy(runtimeRequest = request) }
        recordRequestedPermissions(permissions.toSet())
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
            titleRes = definition.titleRes,
            subtitleRes = definition.subtitleRes,
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
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val permissions: List<AppPermission>,
) {
    Location(
        PermissionGroupKind.Location,
        R.string.permission_name_location,
        R.string.permission_desc_location,
        listOf(AppPermission.FineLocation, AppPermission.CoarseLocation),
    ),
    Calendar(
        PermissionGroupKind.Calendar,
        R.string.permission_name_calendar,
        R.string.permission_desc_calendar,
        listOf(AppPermission.ReadCalendar, AppPermission.WriteCalendar),
    ),
    Media(
        PermissionGroupKind.Media,
        R.string.permission_name_media,
        R.string.permission_desc_media,
        listOf(
            AppPermission.ReadMediaImages,
            AppPermission.ReadMediaVideo,
            AppPermission.ReadMediaAudio,
        ),
    ),
    Microphone(
        PermissionGroupKind.Microphone,
        R.string.permission_name_microphone,
        R.string.permission_desc_microphone,
        listOf(AppPermission.RecordAudio),
    ),
    Bluetooth(
        PermissionGroupKind.Bluetooth,
        R.string.permission_name_bluetooth,
        R.string.permission_desc_bluetooth,
        listOf(AppPermission.BluetoothConnect),
    ),
    Notifications(
        PermissionGroupKind.Notifications,
        R.string.permission_name_notifications,
        R.string.permission_desc_notifications,
        listOf(AppPermission.PostNotifications),
    ),
}
