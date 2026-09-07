package github.ponyhuang.gimi.core.network

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle

/** core network 所有受管目录声明。 */
object NetworkStorage {
    const val HTTP_CACHE_ID = "network.http-cache"

    val HttpCache = ManagedDirectorySpec(
        id = HTTP_CACHE_ID,
        owner = "network",
        area = StorageArea.CACHE,
        relativePath = "network/http",
        lifecycle = StorageLifecycle.CACHE,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
