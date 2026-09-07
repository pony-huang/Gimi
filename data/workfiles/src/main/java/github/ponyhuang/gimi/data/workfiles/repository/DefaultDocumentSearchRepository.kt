package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Applies configured-directory policy to provider-backed document traversal. */
@Singleton
class DefaultDocumentSearchRepository @Inject constructor(
    private val directoryRepository: WorkDirectoryRepository,
    private val gateway: DocumentSearchGateway,
) : DocumentSearchRepository {
    override suspend fun search(query: String, limit: Int): List<WorkFileSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val directories = searchableDirectories()
        val results = directories.flatMap { directory ->
            gateway.search(directory.treeUri, normalizedQuery)
        }
        return results
            .groupBy(WorkFileSearchResult::contentUri)
            .values
            .map { duplicates -> duplicates.maxBy(WorkFileSearchResult::modifiedTimeMillis) }
            .sortedByDescending(WorkFileSearchResult::modifiedTimeMillis)
            .take(limit.coerceIn(1, MAX_RESULTS))
    }

    override suspend fun isAuthorized(contentUri: String): Boolean {
        for (directory in searchableDirectories()) {
            if (gateway.contains(directory.treeUri, contentUri)) return true
        }
        return false
    }

    private suspend fun searchableDirectories() = directoryRepository.observeDirectories()
        .first()
        .filter { directory ->
            directory.enabled && directory.accessStatus == WorkDirectoryAccessStatus.AVAILABLE
        }

    private companion object {
        const val MAX_RESULTS = 50
    }
}
