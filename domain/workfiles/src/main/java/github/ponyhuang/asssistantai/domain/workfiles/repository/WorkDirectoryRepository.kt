package github.ponyhuang.asssistantai.domain.workfiles.repository

import github.ponyhuang.asssistantai.domain.workfiles.model.WorkDirectory
import kotlinx.coroutines.flow.Flow

interface WorkDirectoryRepository {
    fun observeDirectories(): Flow<List<WorkDirectory>>

    fun currentDirectories(): List<WorkDirectory>

    suspend fun addDirectory(uri: String): WorkDirectoryOperationResult

    suspend fun removeDirectory(uri: String): WorkDirectoryOperationResult

    fun contains(uri: String): Boolean
}

sealed interface WorkDirectoryOperationResult {
    data object Success : WorkDirectoryOperationResult

    sealed interface Failure : WorkDirectoryOperationResult {
        data object InvalidDirectory : Failure
        data object PermissionDenied : Failure
        data object PersistenceFailed : Failure
    }
}
