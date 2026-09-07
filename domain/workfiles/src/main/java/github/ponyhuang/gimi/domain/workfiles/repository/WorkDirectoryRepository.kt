package github.ponyhuang.gimi.domain.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import kotlinx.coroutines.flow.Flow

interface WorkDirectoryRepository {
    fun observeDirectories(): Flow<List<WorkDirectory>>

    suspend fun addDirectory(uri: String): WorkDirectoryOperationResult

    suspend fun removeDirectory(id: String): WorkDirectoryOperationResult

    suspend fun setEnabled(id: String, enabled: Boolean): WorkDirectoryOperationResult

    suspend fun reauthorize(id: String, uri: String): WorkDirectoryOperationResult

    suspend fun refreshAccess()

}

sealed interface WorkDirectoryOperationResult {
    /** 目录配置操作已成功完成。 */
    data object Success : WorkDirectoryOperationResult

    sealed interface Failure : WorkDirectoryOperationResult {
        /** 选择结果不是可用的 DocumentsProvider tree。 */
        data object InvalidDirectory : Failure

        /** 无法取得持久读取授权。 */
        data object PermissionDenied : Failure

        /** 新配置未能持久化。 */
        data object PersistenceFailed : Failure

        /** 目录已存在于配置中。 */
        data object DuplicateDirectory : Failure

        /** 与已有父目录或子目录冲突。 */
        data class OverlappingDirectory(val conflictingDirectoryId: String) : Failure

        /** 指定的稳定目录 ID 不存在。 */
        data object NotFound : Failure
    }
}
