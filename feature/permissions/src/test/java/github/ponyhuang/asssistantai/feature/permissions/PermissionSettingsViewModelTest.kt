package github.ponyhuang.asssistantai.feature.permissions

import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.domain.permissions.model.PermissionSnapshot
import github.ponyhuang.asssistantai.domain.permissions.model.RuntimeAppPermissions
import github.ponyhuang.asssistantai.domain.permissions.repository.PermissionRepository
import github.ponyhuang.asssistantai.domain.permissions.usecase.GetPermissionSnapshotUseCase
import github.ponyhuang.asssistantai.domain.permissions.usecase.RecordPermanentlyDeniedPermissionsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertNotNull(viewModel.uiState.value.groups)
        assertTrue(!viewModel.uiState.value.allRuntimeGranted)
    }

    private fun viewModel(repository: PermissionRepository) = PermissionSettingsViewModel(
        getSnapshot = GetPermissionSnapshotUseCase(repository),
        recordPermanentlyDenied = RecordPermanentlyDeniedPermissionsUseCase(repository),
    )

    private fun repository(snapshot: PermissionSnapshot): PermissionRepository = mockk(relaxed = true) {
        every { this@mockk.snapshot() } returns snapshot
    }
}
