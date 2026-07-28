package github.ponyhuang.asssistantai.domain.permissions.usecase

import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.asssistantai.domain.permissions.repository.PermissionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUseCasesTest {

    private val repository = FakePermissionRepository()

    @Test
    fun getPermissionSnapshotReturnsRepositorySnapshot() {
        val snapshot = PermissionSnapshot(
            granted = setOf(AppPermission.RecordAudio),
            permanentlyDenied = setOf(AppPermission.FineLocation),
        )
        repository.snapshotResult = snapshot

        assertSame(snapshot, GetPermissionSnapshotUseCase(repository)())
    }

    @Test
    fun recordPermanentlyDeniedForwardsPermissionSetToRepository() {
        val permissions = setOf(AppPermission.FineLocation, AppPermission.ReadCalendar)

        RecordPermanentlyDeniedPermissionsUseCase(repository)(permissions)

        assertEquals(listOf(permissions), repository.permanentlyDeniedCalls)
    }

    @Test
    fun wasPermissionRequestedDelegatesPerPermission() {
        repository.wasRequestedResult = true

        assertTrue(WasPermissionRequestedUseCase(repository)(AppPermission.BluetoothConnect))
        assertEquals(listOf(AppPermission.BluetoothConnect), repository.wasRequestedCalls)
    }

    @Test
    fun recordRequestedForwardsPermissionSetToRepository() {
        val permissions = setOf(AppPermission.PostNotifications)

        RecordRequestedPermissionsUseCase(repository)(permissions)

        assertEquals(listOf(permissions), repository.requestedCalls)
    }

    private class FakePermissionRepository : PermissionRepository {
        var snapshotResult = PermissionSnapshot(granted = emptySet(), permanentlyDenied = emptySet())
        var wasRequestedResult = false
        val permanentlyDeniedCalls = mutableListOf<Set<AppPermission>>()
        val wasRequestedCalls = mutableListOf<AppPermission>()
        val requestedCalls = mutableListOf<Set<AppPermission>>()

        override fun snapshot(): PermissionSnapshot = snapshotResult

        override fun recordPermanentlyDenied(permissions: Set<AppPermission>) {
            permanentlyDeniedCalls += permissions
        }

        override fun wasRequested(permission: AppPermission): Boolean {
            wasRequestedCalls += permission
            return wasRequestedResult
        }

        override fun recordRequested(permissions: Set<AppPermission>) {
            requestedCalls += permissions
        }
    }
}
