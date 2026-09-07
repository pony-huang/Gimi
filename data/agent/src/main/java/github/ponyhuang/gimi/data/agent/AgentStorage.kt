package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle

/** agent capability 的受管目录声明。 */
object AgentStorage {
    const val ARTIFACTS_ID = "agent.artifacts"

    val Artifacts = ManagedDirectorySpec(
        id = ARTIFACTS_ID,
        owner = "agent",
        area = StorageArea.EXTERNAL_FILES,
        relativePath = "agent/artifacts",
        lifecycle = StorageLifecycle.PERSISTENT,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
