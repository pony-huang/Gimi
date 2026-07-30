package github.ponyhuang.gimi.domain.permissions.repository

import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.model.PermissionSnapshot

interface PermissionRepository {
    fun snapshot(): PermissionSnapshot

    fun recordPermanentlyDenied(permissions: Set<AppPermission>)

    fun wasRequested(permission: AppPermission): Boolean

    fun recordRequested(permissions: Set<AppPermission>)
}
