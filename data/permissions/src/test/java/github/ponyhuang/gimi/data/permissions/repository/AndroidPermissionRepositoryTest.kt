package github.ponyhuang.gimi.data.permissions.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.model.RuntimeAppPermissions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidPermissionRepositoryTest {

    private lateinit var context: Context
    private lateinit var preferences: InMemorySharedPreferences
    private lateinit var repository: AndroidPermissionRepository

    @Before
    fun setUp() {
        preferences = InMemorySharedPreferences()
        context = mockk()
        every {
            context.getSharedPreferences("permission_settings", Context.MODE_PRIVATE)
        } returns preferences
        every { context.packageName } returns PACKAGE_NAME

        mockkStatic(
            ContextCompat::class,
            Settings.System::class,
            NotificationManagerCompat::class,
        )
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_DENIED
        every { Settings.System.canWrite(any()) } returns false
        every {
            NotificationManagerCompat.getEnabledListenerPackages(any())
        } returns emptySet<String>()

        repository = AndroidPermissionRepository(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun snapshotMapsGrantedRuntimePermissionsFromFrameworkCheck() {
        grant(AppPermission.RecordAudio, AppPermission.FineLocation)

        val snapshot = repository.snapshot()

        assertEquals(
            setOf(AppPermission.RecordAudio, AppPermission.FineLocation),
            snapshot.granted,
        )
        assertTrue(snapshot.permanentlyDenied.isEmpty())
    }

    @Test
    fun snapshotReportsAllRuntimePermissionsDeniedWhenFrameworkDeniesAll() {
        val snapshot = repository.snapshot()

        assertTrue(RuntimeAppPermissions.none { snapshot.isGranted(it) })
    }

    @Test
    fun snapshotIncludesWriteSystemSettingsOnlyWhenSystemAllowsWriting() {
        every { Settings.System.canWrite(context) } returns true
        assertTrue(AppPermission.WriteSystemSettings in repository.snapshot().granted)

        every { Settings.System.canWrite(context) } returns false
        assertFalse(AppPermission.WriteSystemSettings in repository.snapshot().granted)
    }

    @Test
    fun snapshotIncludesNotificationListenerOnlyWhenPackageIsAnEnabledListener() {
        every {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        } returns setOf(PACKAGE_NAME)
        assertTrue(AppPermission.NotificationListener in repository.snapshot().granted)

        every {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        } returns setOf("other.package")
        assertFalse(AppPermission.NotificationListener in repository.snapshot().granted)
    }

    @Test
    fun snapshotPrunesPermanentlyDeniedEntriesThatAreGrantedAgain() {
        storePermanentlyDenied(AppPermission.FineLocation, AppPermission.RecordAudio)
        grant(AppPermission.RecordAudio)

        val snapshot = repository.snapshot()

        assertEquals(setOf(AppPermission.FineLocation), snapshot.permanentlyDenied)
        assertEquals(setOf(AppPermission.FineLocation), readStoredPermanentlyDenied())
    }

    @Test
    fun snapshotIgnoresUnknownNamesStoredAsPermanentlyDenied() {
        preferences.edit().putStringSet(
            PERMANENTLY_DENIED_KEY,
            mutableSetOf("NoSuchPermission", AppPermission.FineLocation.name),
        ).commit()

        val snapshot = repository.snapshot()

        assertEquals(setOf(AppPermission.FineLocation), snapshot.permanentlyDenied)
    }

    @Test
    fun recordPermanentlyDeniedMergesStoredAndNewButDropsGranted() {
        storePermanentlyDenied(AppPermission.FineLocation)
        grant(AppPermission.CoarseLocation)

        repository.recordPermanentlyDenied(
            setOf(AppPermission.CoarseLocation, AppPermission.ReadCalendar),
        )

        assertEquals(
            setOf(AppPermission.FineLocation, AppPermission.ReadCalendar),
            readStoredPermanentlyDenied(),
        )
    }

    @Test
    fun wasRequestedReflectsRecordRequestedAcrossCalls() {
        assertFalse(repository.wasRequested(AppPermission.RecordAudio))

        repository.recordRequested(setOf(AppPermission.RecordAudio))
        assertTrue(repository.wasRequested(AppPermission.RecordAudio))
        assertFalse(repository.wasRequested(AppPermission.PostNotifications))

        repository.recordRequested(setOf(AppPermission.PostNotifications))
        assertTrue(repository.wasRequested(AppPermission.RecordAudio))
        assertTrue(repository.wasRequested(AppPermission.PostNotifications))
    }

    @Test
    fun recordRequestedWithEmptySetIsANoOp() {
        repository.recordRequested(emptySet())

        RuntimeAppPermissions.forEach { permission ->
            assertFalse(repository.wasRequested(permission))
        }
    }

    @Test
    fun snapshotPropagatesSecurityExceptionFromFrameworkPermissionCheck() {
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } throws SecurityException("no permission access")

        assertThrows(SecurityException::class.java) { repository.snapshot() }
    }

    private fun grant(vararg permissions: AppPermission) {
        val grantedNames = permissions.mapTo(mutableSetOf(), ::androidName)
        every {
            ContextCompat.checkSelfPermission(context, any())
        } answers {
            if (secondArg<String>() in grantedNames) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
        }
    }

    private fun storePermanentlyDenied(vararg permissions: AppPermission) {
        preferences.edit().putStringSet(
            PERMANENTLY_DENIED_KEY,
            permissions.mapTo(mutableSetOf(), AppPermission::name),
        ).commit()
    }

    private fun readStoredPermanentlyDenied(): Set<AppPermission> =
        preferences.getStringSet(PERMANENTLY_DENIED_KEY, mutableSetOf()).orEmpty()
            .mapTo(mutableSetOf(), AppPermission::valueOf)

    private fun androidName(permission: AppPermission): String = when (permission) {
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
        const val PACKAGE_NAME = "github.ponyhuang.gimi.test"
        const val PERMANENTLY_DENIED_KEY = "permanently_denied_v2"
    }
}
