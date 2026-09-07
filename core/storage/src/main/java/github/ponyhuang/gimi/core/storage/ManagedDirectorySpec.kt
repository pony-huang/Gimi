package github.ponyhuang.gimi.core.storage

/** 目录所依附的 Android 应用存储区域。 */
enum class StorageArea {
    FILES,
    CACHE,
    CODE_CACHE,
    EXTERNAL_FILES,
}

/** 目录内容的预期生命周期。 */
enum class StorageLifecycle {
    PERSISTENT,
    CACHE,
    TEMPORARY,
}

/** 目录内容是否允许进入系统备份。 */
enum class BackupPolicy {
    INCLUDED,
    EXCLUDED,
}

/** 目录是否允许通过应用 FileProvider 对外共享文件。 */
enum class SharingPolicy {
    PRIVATE,
    FILE_PROVIDER,
}

/**
 * 一个由 capability 拥有的受管目录声明。
 *
 * @property id 全局唯一且稳定的逻辑标识，格式为 `<capability>.<purpose>`。
 * @property owner 负责目录内容和清理行为的 capability。
 * @property area Android 应用存储区域。
 * @property relativePath 相对于存储区域根目录的路径。
 * @property lifecycle 内容生命周期，用于约束通用清理行为。
 * @property backupPolicy 系统备份策略。
 * @property sharingPolicy FileProvider 共享策略。
 */
data class ManagedDirectorySpec(
    val id: String,
    val owner: String,
    val area: StorageArea,
    val relativePath: String,
    val lifecycle: StorageLifecycle,
    val backupPolicy: BackupPolicy,
    val sharingPolicy: SharingPolicy,
)
