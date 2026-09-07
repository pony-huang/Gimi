package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persists and validates the document-tree grants explicitly selected by the user. */
@Singleton
class DocumentDirectoryRepository @Inject constructor(
    private val configStore: WorkDirectoryConfigStore,
    private val gateway: DocumentTreeGateway,
) : WorkDirectoryRepository {
    private val mutationMutex = Mutex()

    override fun observeDirectories(): Flow<List<WorkDirectory>> = configStore.directories

    override suspend fun addDirectory(uri: String): WorkDirectoryOperationResult {
        val info = gateway.inspect(uri)
            ?: return WorkDirectoryOperationResult.Failure.InvalidDirectory
        return mutationMutex.withLock {
            val current = configStore.current()
            if (current.any { it.treeUri == info.treeUri }) {
                return@withLock WorkDirectoryOperationResult.Failure.DuplicateDirectory
            }
            val conflict = current.firstOrNull { directory ->
                gateway.relationship(directory.treeUri, info.treeUri) !=
                    DocumentTreeRelationship.DISJOINT
            }
            if (conflict != null) {
                return@withLock if (conflict.treeUri == info.treeUri) {
                    WorkDirectoryOperationResult.Failure.DuplicateDirectory
                } else {
                    WorkDirectoryOperationResult.Failure.OverlappingDirectory(conflict.id)
                }
            }
            if (!gateway.takeReadPermission(info.treeUri)) {
                return@withLock WorkDirectoryOperationResult.Failure.PermissionDenied
            }
            val directory = WorkDirectory(
                id = UUID.randomUUID().toString(),
                treeUri = info.treeUri,
                displayName = info.displayName,
                authority = info.authority,
                enabled = true,
                accessStatus = gateway.accessStatus(info.treeUri),
                addedAtEpochMillis = System.currentTimeMillis(),
            )
            if (!replaceSafely(current + directory)) {
                gateway.releaseReadPermission(info.treeUri)
                return@withLock WorkDirectoryOperationResult.Failure.PersistenceFailed
            }
            WorkDirectoryOperationResult.Success
        }
    }

    override suspend fun removeDirectory(id: String): WorkDirectoryOperationResult =
        mutationMutex.withLock {
            val current = configStore.current()
            val removed = current.firstOrNull { it.id == id }
                ?: return@withLock WorkDirectoryOperationResult.Failure.NotFound
            if (!replaceSafely(current.filterNot { it.id == id })) {
                return@withLock WorkDirectoryOperationResult.Failure.PersistenceFailed
            }
            gateway.releaseReadPermission(removed.treeUri)
            WorkDirectoryOperationResult.Success
        }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ): WorkDirectoryOperationResult = mutationMutex.withLock {
        val current = configStore.current()
        if (current.none { it.id == id }) {
            return@withLock WorkDirectoryOperationResult.Failure.NotFound
        }
        val updated = current.map { directory ->
            if (directory.id == id) directory.copy(enabled = enabled) else directory
        }
        if (replaceSafely(updated)) {
            WorkDirectoryOperationResult.Success
        } else {
            WorkDirectoryOperationResult.Failure.PersistenceFailed
        }
    }

    override suspend fun reauthorize(
        id: String,
        uri: String,
    ): WorkDirectoryOperationResult {
        val info = gateway.inspect(uri)
            ?: return WorkDirectoryOperationResult.Failure.InvalidDirectory
        return mutationMutex.withLock {
            val current = configStore.current()
            val existing = current.firstOrNull { it.id == id }
                ?: return@withLock WorkDirectoryOperationResult.Failure.NotFound
            val conflict = current.firstOrNull { directory ->
                directory.id != id && gateway.relationship(directory.treeUri, info.treeUri) !=
                    DocumentTreeRelationship.DISJOINT
            }
            if (conflict != null) {
                return@withLock if (conflict.treeUri == info.treeUri) {
                    WorkDirectoryOperationResult.Failure.DuplicateDirectory
                } else {
                    WorkDirectoryOperationResult.Failure.OverlappingDirectory(conflict.id)
                }
            }
            if (!gateway.takeReadPermission(info.treeUri)) {
                return@withLock WorkDirectoryOperationResult.Failure.PermissionDenied
            }
            val replacement = existing.copy(
                treeUri = info.treeUri,
                displayName = info.displayName,
                authority = info.authority,
                accessStatus = gateway.accessStatus(info.treeUri),
            )
            val updated = current.map { directory ->
                if (directory.id == id) replacement else directory
            }
            if (!replaceSafely(updated)) {
                gateway.releaseReadPermission(info.treeUri)
                return@withLock WorkDirectoryOperationResult.Failure.PersistenceFailed
            }
            if (existing.treeUri != info.treeUri) {
                gateway.releaseReadPermission(existing.treeUri)
            }
            WorkDirectoryOperationResult.Success
        }
    }

    override suspend fun refreshAccess() = mutationMutex.withLock {
        val current = configStore.current()
        val updated = current.map { directory ->
            directory.copy(accessStatus = gateway.accessStatus(directory.treeUri))
        }
        replaceSafely(updated)
        Unit
    }

    private suspend fun replaceSafely(directories: List<WorkDirectory>): Boolean = try {
        configStore.replace(directories)
        true
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        false
    }
}
