package github.ponyhuang.gimi.domain.skills.repository

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeInstalled(): Flow<List<InstalledSkill>>

    suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport

    suspend fun commitImport(preparedId: String, allowReplace: Boolean)

    suspend fun discardImport(preparedId: String)

    suspend fun remove(name: String)
}
