package github.ponyhuang.asssistantai.domain.workfiles.repository

import github.ponyhuang.asssistantai.domain.workfiles.model.WorkDirectory
import kotlinx.coroutines.flow.Flow

interface WorkDirectoryRepository {
    fun observeDirectories(): Flow<List<WorkDirectory>>

    fun currentDirectories(): List<WorkDirectory>

    fun addDirectory(uri: String): Boolean

    fun removeDirectory(uri: String)

    fun contains(uri: String): Boolean
}
