package github.ponyhuang.asssistantai.domain.workfiles.usecase

import github.ponyhuang.asssistantai.domain.workfiles.repository.WorkDirectoryRepository
import javax.inject.Inject

class ObserveWorkDirectoriesUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    operator fun invoke() = repository.observeDirectories()
}

class AddWorkDirectoryUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    operator fun invoke(uri: String) = repository.addDirectory(uri)
}

class RemoveWorkDirectoryUseCase @Inject constructor(
    private val repository: WorkDirectoryRepository,
) {
    operator fun invoke(uri: String) = repository.removeDirectory(uri)
}
