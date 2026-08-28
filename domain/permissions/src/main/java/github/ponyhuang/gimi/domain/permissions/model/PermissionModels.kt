package github.ponyhuang.gimi.domain.permissions.model

enum class AppPermission {
    FineLocation,
    CoarseLocation,
    ReadCalendar,
    WriteCalendar,
    ReadMediaImages,
    ReadMediaVideo,
    ReadMediaAudio,
    RecordAudio,
    BluetoothConnect,
    PostNotifications,
    WriteSystemSettings,
    NotificationListener,
    UsageStats,
}

data class PermissionSnapshot(
    val granted: Set<AppPermission>,
    val permanentlyDenied: Set<AppPermission>,
) {
    fun isGranted(permission: AppPermission) = permission in granted
}

val RuntimeAppPermissions = setOf(
    AppPermission.FineLocation,
    AppPermission.CoarseLocation,
    AppPermission.ReadCalendar,
    AppPermission.WriteCalendar,
    AppPermission.ReadMediaImages,
    AppPermission.ReadMediaVideo,
    AppPermission.ReadMediaAudio,
    AppPermission.RecordAudio,
    AppPermission.BluetoothConnect,
    AppPermission.PostNotifications,
)
