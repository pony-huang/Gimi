package github.ponyhuang.gimi.domain.workfiles.usecase

import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import javax.inject.Inject

class ObserveWorkDirectoriesUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    operator fun invoke() = repository.observeDirectories()
}

class AddWorkDirectoryUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    suspend operator fun invoke(uri: String) = repository.addDirectory(uri)
}

class RemoveWorkDirectoryUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    suspend operator fun invoke(id: String) = repository.removeDirectory(id)
}

class SetWorkDirectoryEnabledUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    suspend operator fun invoke(id: String, enabled: Boolean) = repository.setEnabled(id, enabled)
}

class RefreshWorkDirectoryAccessUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    suspend operator fun invoke() = repository.refreshAccess()
}

class ReauthorizeWorkDirectoryUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    suspend operator fun invoke(id: String, uri: String) = repository.reauthorize(id, uri)
}
