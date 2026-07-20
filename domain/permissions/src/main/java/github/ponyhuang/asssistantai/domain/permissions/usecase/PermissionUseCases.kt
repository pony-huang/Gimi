package github.ponyhuang.asssistantai.domain.permissions.usecase

import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.domain.permissions.repository.PermissionRepository
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
