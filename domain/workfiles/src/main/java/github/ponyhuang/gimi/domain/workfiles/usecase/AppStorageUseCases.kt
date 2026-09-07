package github.ponyhuang.gimi.domain.workfiles.usecase

import github.ponyhuang.gimi.domain.workfiles.repository.AppStorageManagementRepository
import javax.inject.Inject

class LoadAppStorageSummaryUseCase @Inject constructor(
    private val repository: AppStorageManagementRepository,
) {
    suspend operator fun invoke() = repository.loadSummary()
}

class ClearReclaimableStorageUseCase @Inject constructor(
    private val repository: AppStorageManagementRepository,
) {
    suspend operator fun invoke() = repository.clearReclaimable()
}
