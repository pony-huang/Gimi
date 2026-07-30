package github.ponyhuang.gimi.domain.skills.usecase

import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import javax.inject.Inject

class ObserveInstalledSkillsUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    operator fun invoke() = repository.observeInstalled()
}

class PrepareSkillImportUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    suspend operator fun invoke(source: SkillImportSource) = repository.prepareImport(source)
}

class CommitSkillImportUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    suspend operator fun invoke(preparedId: String, allowReplace: Boolean) =
        repository.commitImport(preparedId, allowReplace)
}

class DiscardSkillImportUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    suspend operator fun invoke(preparedId: String) = repository.discardImport(preparedId)
}

class RemoveSkillUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    suspend operator fun invoke(name: String) = repository.remove(name)
}
