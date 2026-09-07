package github.ponyhuang.gimi.domain.workfiles.model

/**
 * 设置页展示的应用受管存储汇总。
 *
 * @property totalBytes 全部可读取受管目录占用空间。
 * @property persistentBytes 持久数据占用空间。
 * @property cacheBytes 可重建缓存占用空间。
 * @property temporaryBytes 临时文件占用空间。
 * @property unreadableDirectoryCount 无法读取并因此未计入大小的目录数量。
 */
data class AppStorageSummary(
    val totalBytes: Long,
    val persistentBytes: Long,
    val cacheBytes: Long,
    val temporaryBytes: Long,
    val unreadableDirectoryCount: Int,
) {
    val reclaimableBytes: Long get() = cacheBytes + temporaryBytes
}

/**
 * 一次安全存储清理的结果。
 *
 * @property clearedDirectoryCount 成功交由 owner 清理的目录数量。
 * @property failedDirectoryCount 未能清理的目录数量。
 * @property reclaimedBytes 清理前后统计得到的已释放字节数。
 */
data class StorageClearSummary(
    val clearedDirectoryCount: Int,
    val failedDirectoryCount: Int,
    val reclaimedBytes: Long,
)
