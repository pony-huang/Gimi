package github.ponyhuang.asssistantai.domain.permissions.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionModelsTest {

    @Test
    fun snapshotIsGrantedReflectsGrantedSetMembership() {
        val snapshot = PermissionSnapshot(
            granted = setOf(AppPermission.RecordAudio, AppPermission.FineLocation),
            permanentlyDenied = setOf(AppPermission.PostNotifications),
        )

        assertTrue(snapshot.isGranted(AppPermission.RecordAudio))
        assertTrue(snapshot.isGranted(AppPermission.FineLocation))
        assertFalse(snapshot.isGranted(AppPermission.PostNotifications))
        assertFalse(snapshot.isGranted(AppPermission.ReadCalendar))
    }

    @Test
    fun snapshotSeparatesPlainDeniedFromPermanentlyDenied() {
        // 不在 granted 也不出现 permanentlyDenied 的权限表示“普通拒绝，仍可再请求”。
        val snapshot = PermissionSnapshot(
            granted = setOf(AppPermission.RecordAudio),
            permanentlyDenied = setOf(AppPermission.FineLocation),
        )

        val merelyDenied = AppPermission.ReadCalendar
        assertFalse(snapshot.isGranted(merelyDenied))
        assertFalse(merelyDenied in snapshot.permanentlyDenied)

        assertFalse(snapshot.isGranted(AppPermission.FineLocation))
        assertTrue(AppPermission.FineLocation in snapshot.permanentlyDenied)
    }

    @Test
    fun runtimePermissionsCoverExactlyTheRuntimeRequestableEntries() {
        assertEquals(
            setOf(
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
            ),
            RuntimeAppPermissions,
        )
    }

    @Test
    fun runtimePermissionsExcludeSpecialPermissions() {
        assertFalse(AppPermission.WriteSystemSettings in RuntimeAppPermissions)
        assertFalse(AppPermission.NotificationListener in RuntimeAppPermissions)
    }

    @Test
    fun runtimePermissionsAreASubsetOfAllDeclaredPermissions() {
        assertTrue(AppPermission.entries.size > RuntimeAppPermissions.size)
        assertTrue(AppPermission.entries.containsAll(RuntimeAppPermissions))
    }
}
