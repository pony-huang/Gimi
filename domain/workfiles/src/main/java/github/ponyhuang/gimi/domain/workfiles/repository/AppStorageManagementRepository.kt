package github.ponyhuang.gimi.domain.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.AppStorageSummary
import github.ponyhuang.gimi.domain.workfiles.model.StorageClearSummary

interface AppStorageManagementRepository {
    suspend fun loadSummary(): AppStorageSummary

    suspend fun clearReclaimable(): StorageClearSummary
}
