package github.ponyhuang.asssistantai.feature.permissions

import androidx.annotation.StringRes
import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission

enum class PermissionGroupKind {
    Location,
    Calendar,
    Media,
    Microphone,
    Bluetooth,
    Notifications,
}

enum class PermissionGroupStatus {
    Granted,
    Denied,
    PartiallyGranted,
}

data class PermissionGroupUiModel(
    val kind: PermissionGroupKind,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val permissions: List<AppPermission>,
    val status: PermissionGroupStatus,
)

data class RuntimePermissionRequest(
    val id: Int,
    val permissions: List<AppPermission>,
    val previouslyRequested: Set<AppPermission>,
)

enum class PermissionSettingsDestination {
    ApplicationDetails,
    WriteSystemSettings,
    NotificationListener,
}

data class PermissionSettingsRequest(
    val id: Int,
    val destination: PermissionSettingsDestination,
)

data class PermissionSettingsUiState(
    val groups: List<PermissionGroupUiModel> = emptyList(),
    val granted: Set<AppPermission> = emptySet(),
    val permanentlyDenied: Set<AppPermission> = emptySet(),
    val allRuntimeGranted: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val runtimeRequest: RuntimePermissionRequest? = null,
    val settingsRequest: PermissionSettingsRequest? = null,
)

sealed interface PermissionSettingsAction {
    data object Refresh : PermissionSettingsAction
    data object RequestAllRuntimePermissions : PermissionSettingsAction
    data class RequestGroup(val kind: PermissionGroupKind) : PermissionSettingsAction
    data class RuntimePermissionsResult(
        val permanentlyDenied: Set<AppPermission>,
    ) : PermissionSettingsAction
    data object OpenApplicationSettings : PermissionSettingsAction
    data object OpenWriteSettings : PermissionSettingsAction
    data object OpenNotificationAccess : PermissionSettingsAction
    data class RuntimeRequestHandled(val requestId: Int) : PermissionSettingsAction
    data class SettingsRequestHandled(val requestId: Int) : PermissionSettingsAction
}
