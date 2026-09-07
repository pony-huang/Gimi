package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle

/** skills capability 的受管目录声明。 */
object SkillsStorage {
    const val INSTALLED_ID = "skills.installed"
    const val IMPORTS_ID = "skills.imports"

    val Installed = ManagedDirectorySpec(
        id = INSTALLED_ID,
        owner = "skills",
        area = StorageArea.FILES,
        relativePath = "skills/installed",
        lifecycle = StorageLifecycle.PERSISTENT,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
    val Imports = ManagedDirectorySpec(
        id = IMPORTS_ID,
        owner = "skills",
        area = StorageArea.CACHE,
        relativePath = "skills/imports",
        lifecycle = StorageLifecycle.TEMPORARY,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
