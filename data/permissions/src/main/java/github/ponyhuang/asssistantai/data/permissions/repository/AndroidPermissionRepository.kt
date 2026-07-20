package github.ponyhuang.asssistantai.data.permissions.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.asssistantai.domain.permissions.model.RuntimeAppPermissions
import github.ponyhuang.asssistantai.domain.permissions.repository.PermissionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun snapshot(): PermissionSnapshot {
        val granted = buildSet {
            RuntimeAppPermissions.filterTo(this) { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission.androidName(),
                ) == PackageManager.PERMISSION_GRANTED
            }
            if (Settings.System.canWrite(context)) add(AppPermission.WriteSystemSettings)
            if (
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            ) {
                add(AppPermission.NotificationListener)
            }
        }
        val denied = readPermanentlyDenied().filterTo(mutableSetOf()) { it !in granted }
        persistPermanentlyDenied(denied)
        return PermissionSnapshot(granted = granted, permanentlyDenied = denied)
    }

    override fun recordPermanentlyDenied(permissions: Set<AppPermission>) {
        val granted = snapshot().granted
        persistPermanentlyDenied(
            (readPermanentlyDenied() + permissions).filterTo(mutableSetOf()) { it !in granted },
        )
    }

    private fun readPermanentlyDenied(): Set<AppPermission> = preferences
        .getStringSet(PERMANENTLY_DENIED_KEY, emptySet())
        .orEmpty()
        .mapNotNullTo(mutableSetOf()) { value ->
            runCatching { AppPermission.valueOf(value) }.getOrNull()
        }

    private fun persistPermanentlyDenied(permissions: Set<AppPermission>) {
        preferences.edit {
            putStringSet(PERMANENTLY_DENIED_KEY, permissions.mapTo(mutableSetOf()) { it.name })
        }
    }

    private fun AppPermission.androidName(): String = when (this) {
        AppPermission.FineLocation -> Manifest.permission.ACCESS_FINE_LOCATION
        AppPermission.CoarseLocation -> Manifest.permission.ACCESS_COARSE_LOCATION
        AppPermission.ReadCalendar -> Manifest.permission.READ_CALENDAR
        AppPermission.WriteCalendar -> Manifest.permission.WRITE_CALENDAR
        AppPermission.ReadMediaImages -> Manifest.permission.READ_MEDIA_IMAGES
        AppPermission.ReadMediaVideo -> Manifest.permission.READ_MEDIA_VIDEO
        AppPermission.ReadMediaAudio -> Manifest.permission.READ_MEDIA_AUDIO
        AppPermission.RecordAudio -> Manifest.permission.RECORD_AUDIO
        AppPermission.BluetoothConnect -> Manifest.permission.BLUETOOTH_CONNECT
        AppPermission.PostNotifications -> Manifest.permission.POST_NOTIFICATIONS
        AppPermission.WriteSystemSettings,
        AppPermission.NotificationListener,
        -> error("Special permissions do not have runtime permission names.")
    }

    private companion object {
        const val PREFERENCES_NAME = "permission_settings"
        const val PERMANENTLY_DENIED_KEY = "permanently_denied_v2"
    }
}
