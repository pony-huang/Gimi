package github.ponyhuang.gimi.data.workfiles

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle

const val WORKFILES_CONFIG_DIRECTORY_ID = "workfiles.config"

/** Persistent storage declaration for structured work-directory configuration. */
val workFilesConfigDirectorySpec = ManagedDirectorySpec(
    id = WORKFILES_CONFIG_DIRECTORY_ID,
    owner = "data:workfiles",
    area = StorageArea.FILES,
    relativePath = "workfiles/config",
    lifecycle = StorageLifecycle.PERSISTENT,
    backupPolicy = BackupPolicy.INCLUDED,
    sharingPolicy = SharingPolicy.PRIVATE,
)
