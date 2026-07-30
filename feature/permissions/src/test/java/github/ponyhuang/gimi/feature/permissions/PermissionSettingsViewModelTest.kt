package github.ponyhuang.gimi.feature.permissions

import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.gimi.domain.permissions.model.RuntimeAppPermissions
import github.ponyhuang.gimi.domain.permissions.repository.PermissionRepository
import github.ponyhuang.gimi.domain.permissions.usecase.GetPermissionSnapshotUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.RecordPermanentlyDeniedPermissionsUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.RecordRequestedPermissionsUseCase
import github.ponyhuang.gimi.domain.permissions.usecase.WasPermissionRequestedUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSettingsViewModelTest {
    @Test
    fun snapshotBuildsGroupedStatusesAndRequestsMissingMicrophone() {
        val repository = repository(
            PermissionSnapshot(
                granted = RuntimeAppPermissions - AppPermission.RecordAudio,
                permanentlyDenied = emptySet(),
            ),
        )
        val viewModel = viewModel(repository)

        val microphone = viewModel.uiState.value.groups.single {
            it.kind == PermissionGroupKind.Microphone
        }
        assertEquals(PermissionGroupStatus.Denied, microphone.status)

        viewModel.onAction(PermissionSettingsAction.RequestGroup(PermissionGroupKind.Microphone))

        assertEquals(
            listOf(AppPermission.RecordAudio),
            viewModel.uiState.value.runtimeRequest?.permissions,
        )
        assertEquals(viewModel.uiState.value.runtimeRequest, viewModel.pendingRuntimeRequest)
    }

    @Test
    fun permanentlyDeniedGroupRoutesToApplicationSettings() {
        val repository = repository(
            PermissionSnapshot(
                granted = RuntimeAppPermissions - AppPermission.RecordAudio,
                permanentlyDenied = setOf(AppPermission.RecordAudio),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.onAction(PermissionSettingsAction.RequestGroup(PermissionGroupKind.Microphone))

        assertEquals(
            PermissionSettingsDestination.ApplicationDetails,
            viewModel.uiState.value.settingsRequest?.destination,
        )
    }

    @Test
    fun permissionResultPersistsBlockedPermissionsAndRefreshesSnapshot() {
        val repository = repository(PermissionSnapshot(emptySet(), emptySet()))
        val viewModel = viewModel(repository)

        viewModel.onAction(
            PermissionSettingsAction.RuntimePermissionsResult(
                setOf(AppPermission.RecordAudio),
            ),
        )

        verify { repository.recordPermanentlyDenied(setOf(AppPermission.RecordAudio)) }
        assertNull(viewModel.pendingRuntimeRequest)
        assertNotNull(viewModel.uiState.value.groups)
        assertTrue(!viewModel.uiState.value.allRuntimeGranted)
    }

    @Test
    fun firstRuntimeRequestIsNotMarkedAsPreviouslyRequested() {
        val repository = repository(
            PermissionSnapshot(
                granted = RuntimeAppPermissions - AppPermission.RecordAudio,
                permanentlyDenied = emptySet(),
            ),
        )
        every { repository.wasRequested(AppPermission.RecordAudio) } returns false
        val viewModel = viewModel(repository)

        viewModel.onAction(PermissionSettingsAction.RequestGroup(PermissionGroupKind.Microphone))

        assertTrue(viewModel.uiState.value.runtimeRequest?.previouslyRequested.orEmpty().isEmpty())
        verify { repository.recordRequested(setOf(AppPermission.RecordAudio)) }
    }

    private fun viewModel(repository: PermissionRepository) = PermissionSettingsViewModel(
        getSnapshot = GetPermissionSnapshotUseCase(repository),
        recordPermanentlyDenied = RecordPermanentlyDeniedPermissionsUseCase(repository),
        wasPermissionRequested = WasPermissionRequestedUseCase(repository),
        recordRequestedPermissions = RecordRequestedPermissionsUseCase(repository),
    )

    private fun repository(snapshot: PermissionSnapshot): PermissionRepository = mockk(relaxed = true) {
        every { this@mockk.snapshot() } returns snapshot
    }
}
