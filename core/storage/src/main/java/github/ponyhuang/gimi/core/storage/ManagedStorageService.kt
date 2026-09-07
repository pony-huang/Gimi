package github.ponyhuang.gimi.core.storage

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** 读取受管目录时得到的可诊断状态。 */
enum class DirectoryReadStatus {
    AVAILABLE,
    MISSING,
    UNREADABLE,
}

/** 清理一个受管目录后的结果。 */
enum class StorageClearResult {
    CLEARED,
    PROTECTED,
    OWNER_UNAVAILABLE,
    FAILED,
}

/**
 * 单个受管目录的空间快照。
 *
 * @property spec 目录静态声明。
 * @property bytes 当前可读取内容占用字节数。
 * @property status 当前文件系统读取状态。
 */
data class ManagedDirectorySnapshot(
    val spec: ManagedDirectorySpec,
    val bytes: Long,
    val status: DirectoryReadStatus,
)

/**
 * 全部受管目录的空间快照。
 *
 * @property directories 按目录 ID 排序的明细。
 * @property bytesByLifecycle 按生命周期汇总的字节数。
 */
data class ManagedStorageSnapshot(
    val directories: List<ManagedDirectorySnapshot>,
    val bytesByLifecycle: Map<StorageLifecycle, Long>,
)

/** capability 对自己可清理目录执行语义安全的清理。 */
interface StorageMaintenanceHandler {
    val owner: String

    suspend fun clear(spec: ManagedDirectorySpec, directory: File): Boolean
}

/** 默认文件树清理器；capability 必须以自己的 owner 显式注册后才会生效。 */
class FileTreeStorageMaintenanceHandler(
    override val owner: String,
) : StorageMaintenanceHandler {
    override suspend fun clear(spec: ManagedDirectorySpec, directory: File): Boolean {
        if (spec.owner != owner || spec.lifecycle == StorageLifecycle.PERSISTENT) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        val children = directory.listFiles() ?: return false
        if (!children.all(File::deleteRecursively)) return false
        return directory.isDirectory || directory.mkdirs()
    }
}

/** 提供空间统计，并把安全清理委托给目录 owner。 */
class ManagedStorageService(
    private val registry: StorageRegistry,
    handlers: Set<StorageMaintenanceHandler>,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val handlersByOwner = handlers.associateBy(StorageMaintenanceHandler::owner).also { indexed ->
        require(indexed.size == handlers.size) { "Storage maintenance handler owners must be unique" }
    }

    suspend fun snapshot(): ManagedStorageSnapshot = withContext(ioDispatcher) {
        val directories = registry.specs.map { spec -> readSnapshot(spec) }
        ManagedStorageSnapshot(
            directories = directories,
            bytesByLifecycle = StorageLifecycle.entries.associateWith { lifecycle ->
                directories.filter { it.spec.lifecycle == lifecycle }.sumOf(ManagedDirectorySnapshot::bytes)
            },
        )
    }

    suspend fun clear(id: String): StorageClearResult = withContext(ioDispatcher) {
        val spec = registry.requireSpec(id)
        if (spec.lifecycle == StorageLifecycle.PERSISTENT) return@withContext StorageClearResult.PROTECTED
        val handler = handlersByOwner[spec.owner] ?: return@withContext StorageClearResult.OWNER_UNAVAILABLE
        if (handler.clear(spec, registry.resolve(id))) {
            StorageClearResult.CLEARED
        } else {
            StorageClearResult.FAILED
        }
    }

    private fun readSnapshot(spec: ManagedDirectorySpec): ManagedDirectorySnapshot {
        val directory = registry.resolve(spec.id)
        if (!directory.exists()) return ManagedDirectorySnapshot(spec, 0L, DirectoryReadStatus.MISSING)
        val bytes = directorySize(directory)
            ?: return ManagedDirectorySnapshot(spec, 0L, DirectoryReadStatus.UNREADABLE)
        return ManagedDirectorySnapshot(spec, bytes, DirectoryReadStatus.AVAILABLE)
    }

    private fun directorySize(file: File): Long? {
        if (file.isFile) return file.length()
        val children = file.listFiles() ?: return null
        var total = 0L
        children.forEach { child ->
            total += directorySize(child) ?: return null
        }
        return total
    }
}
