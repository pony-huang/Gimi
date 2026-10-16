package github.ponyhuang.gimi.domain.skills.usecase

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 非交互安装流程：拒绝未经允许的覆盖，且不向调用者遗留暂存票据。 */
class InstallSkillArchiveUseCase @Inject constructor(
    private val repository: SkillRepository,
) {
    suspend operator fun invoke(source: SkillImportSource, allowReplace: Boolean = false): InstalledSkill {
        val prepared = repository.prepareImport(source)
        try {
            if (prepared.replacesExisting && !allowReplace) {
                throw SkillImportFailure(
                    SkillImportFailure.Reason.ReplacementNotAllowed,
                    "Replacing an installed skill requires explicit confirmation.",
                )
            }
            // commit 仍须再次检查同名状态，不能只相信准备阶段的快照。
            repository.commitImport(prepared.id, allowReplace)
            return InstalledSkill(prepared.name, prepared.description)
        } finally {
            withContext(NonCancellable) {
                try {
                    repository.discardImport(prepared.id)
                } catch (_: SkillImportFailure) {
                    // 清理失败不覆盖安装结果或取消信号；缓存目录会在下次初始化时回收。
                }
            }
        }
    }
}
