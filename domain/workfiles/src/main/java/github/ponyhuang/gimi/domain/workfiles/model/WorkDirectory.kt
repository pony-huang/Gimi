package github.ponyhuang.gimi.domain.workfiles.model

/** 工作目录当前的持久读取授权健康状态。 */
enum class WorkDirectoryAccessStatus {
    AVAILABLE,
    PERMISSION_LOST,
    PROVIDER_UNAVAILABLE,
}

/**
 * 用户显式授权给本地文档搜索的 SAF tree 目录。
 *
 * @property id 应用生成的稳定目录标识。
 * @property treeUri DocumentsProvider tree URI 的字符串形式。
 * @property displayName 由 provider 返回的用户可读目录名。
 * @property authority DocumentsProvider authority，用于诊断来源。
 * @property enabled 用户是否允许搜索该目录。
 * @property accessStatus 当前持久读取授权健康状态。
 * @property addedAtEpochMillis 目录首次加入配置的时间。
 */
data class WorkDirectory(
    val id: String,
    val treeUri: String,
    val displayName: String,
    val authority: String,
    val enabled: Boolean,
    val accessStatus: WorkDirectoryAccessStatus,
    val addedAtEpochMillis: Long,
)
