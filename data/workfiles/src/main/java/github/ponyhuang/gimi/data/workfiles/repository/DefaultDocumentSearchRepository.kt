package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 共享存储搜索的权限、查询规范化和结果上限策略。 */
@Singleton
class DefaultDocumentSearchRepository @Inject constructor(
    private val gateway: DocumentSearchGateway,
) : DocumentSearchRepository {
    override fun hasAccess(): Boolean = gateway.hasAccess()

    override suspend fun search(query: String, limit: Int): List<WorkFileSearchResult> {
        val normalized = query.trim()
        if (normalized.isEmpty() || !hasAccess()) return emptyList()
        return gateway.search(normalized, limit.coerceIn(1, 50))
    }

    override suspend fun isAuthorized(contentUri: String): Boolean =
        hasAccess() && gateway.contains(contentUri)
}
