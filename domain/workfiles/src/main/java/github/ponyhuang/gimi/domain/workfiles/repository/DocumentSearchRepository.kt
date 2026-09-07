package github.ponyhuang.gimi.domain.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult

interface DocumentSearchRepository {
    suspend fun search(query: String, limit: Int = 50): List<WorkFileSearchResult>

    suspend fun isAuthorized(contentUri: String): Boolean
}
