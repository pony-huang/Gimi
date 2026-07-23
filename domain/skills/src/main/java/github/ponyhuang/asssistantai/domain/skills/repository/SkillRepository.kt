package github.ponyhuang.asssistantai.domain.skills.repository

import github.ponyhuang.asssistantai.domain.skills.model.InstalledSkill
import github.ponyhuang.asssistantai.domain.skills.model.PreparedSkillImport
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportSource
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeInstalled(): Flow<List<InstalledSkill>>

    suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport

    suspend fun commitImport(preparedId: String, allowReplace: Boolean)

    suspend fun discardImport(preparedId: String)

    suspend fun remove(name: String)
}
