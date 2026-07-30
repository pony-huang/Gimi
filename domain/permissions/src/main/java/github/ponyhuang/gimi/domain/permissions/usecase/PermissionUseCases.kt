package github.ponyhuang.gimi.domain.permissions.usecase

import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.repository.PermissionRepository
import javax.inject.Inject

class GetPermissionSnapshotUseCase @Inject constructor(
    private val repository: PermissionRepository,
) {
    operator fun invoke() = repository.snapshot()
}

class RecordPermanentlyDeniedPermissionsUseCase @Inject constructor(
    private val repository: PermissionRepository,
) {
    operator fun invoke(permissions: Set<AppPermission>) =
        repository.recordPermanentlyDenied(permissions)
}

class WasPermissionRequestedUseCase @Inject constructor(
    private val repository: PermissionRepository,
) {
    operator fun invoke(permission: AppPermission) = repository.wasRequested(permission)
}

class RecordRequestedPermissionsUseCase @Inject constructor(
    private val repository: PermissionRepository,
) {
    operator fun invoke(permissions: Set<AppPermission>) = repository.recordRequested(permissions)
}
