package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.core.storage.DirectoryReadStatus
import github.ponyhuang.gimi.core.storage.ManagedStorageService
import github.ponyhuang.gimi.core.storage.ManagedStorageSnapshot
import github.ponyhuang.gimi.core.storage.StorageClearResult
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.domain.workfiles.model.AppStorageSummary
import github.ponyhuang.gimi.domain.workfiles.model.StorageClearSummary
import github.ponyhuang.gimi.domain.workfiles.repository.AppStorageManagementRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Maps app-wide managed storage operations into the work-files settings domain. */
@Singleton
class DefaultAppStorageManagementRepository @Inject constructor(
    private val service: ManagedStorageService,
) : AppStorageManagementRepository {
    override suspend fun loadSummary(): AppStorageSummary = service.snapshot().toDomain()

    override suspend fun clearReclaimable(): StorageClearSummary {
        val before = service.snapshot()
        val results = before.directories
            .filter { it.spec.lifecycle != StorageLifecycle.PERSISTENT }
            .map { directory -> service.clear(directory.spec.id) }
        val after = service.snapshot()
        return StorageClearSummary(
            clearedDirectoryCount = results.count { it == StorageClearResult.CLEARED },
            failedDirectoryCount = results.count { it != StorageClearResult.CLEARED },
            reclaimedBytes = (before.reclaimableBytes() - after.reclaimableBytes()).coerceAtLeast(0L),
        )
    }

    private fun ManagedStorageSnapshot.toDomain() = AppStorageSummary(
        totalBytes = bytesByLifecycle.values.sum(),
        persistentBytes = bytesByLifecycle[StorageLifecycle.PERSISTENT] ?: 0L,
        cacheBytes = bytesByLifecycle[StorageLifecycle.CACHE] ?: 0L,
        temporaryBytes = bytesByLifecycle[StorageLifecycle.TEMPORARY] ?: 0L,
        unreadableDirectoryCount = directories.count {
            it.status == DirectoryReadStatus.UNREADABLE
        },
    )

    private fun ManagedStorageSnapshot.reclaimableBytes(): Long =
        (bytesByLifecycle[StorageLifecycle.CACHE] ?: 0L) +
            (bytesByLifecycle[StorageLifecycle.TEMPORARY] ?: 0L)
}
