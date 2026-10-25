package github.ponyhuang.gimi.domain.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult

/** 共享外部存储的文件搜索和访问校验契约。 */
interface DocumentSearchRepository {
    /** 是否已获得搜索共享外部存储所需的访问权限。 */
    fun hasAccess(): Boolean
    suspend fun search(query: String, limit: Int = 50): List<WorkFileSearchResult>
    suspend fun isAuthorized(contentUri: String): Boolean
}
