package github.ponyhuang.gimi.domain.skills.usecase

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SkillUseCasesTest {
    @Test
    fun useCasesPreserveRepositoryInputsAndResults() = runTest {
        val repository = RecordingSkillRepository()
        val source = SkillImportSource.Url("https://example.test/skill.zip")

        assertSame(repository.installed, ObserveInstalledSkillsUseCase(repository)())
        assertEquals(repository.prepared, PrepareSkillImportUseCase(repository)(source))
        CommitSkillImportUseCase(repository)(repository.prepared.id, allowReplace = true)
        DiscardSkillImportUseCase(repository)(repository.prepared.id)
        RemoveSkillUseCase(repository)("calendar")

        assertEquals(source, repository.preparedSource)
        assertEquals(repository.prepared.id to true, repository.committed)
        assertEquals(repository.prepared.id, repository.discarded)
        assertEquals("calendar", repository.removed)
    }

    @Test
    fun prepareImportPropagatesTypedDomainFailure() {
        val expected = SkillImportFailure(
            reason = SkillImportFailure.Reason.InvalidStructure,
            message = "invalid archive",
        )
        val repository = RecordingSkillRepository(prepareFailure = expected)

        val actual = assertThrows(SkillImportFailure::class.java) {
            kotlinx.coroutines.test.runTest {
                PrepareSkillImportUseCase(repository)(SkillImportSource.LocalDocument("content://skill"))
            }
        }

        assertSame(expected, actual)
    }

    private class RecordingSkillRepository(
        private val prepareFailure: SkillImportFailure? = null,
    ) : SkillRepository {
        val installed = flowOf(listOf(InstalledSkill("calendar", "Calendar helpers")))
        val prepared = PreparedSkillImport("prepared-id", "calendar", "Calendar helpers", false)
        var preparedSource: SkillImportSource? = null
        var committed: Pair<String, Boolean>? = null
        var discarded: String? = null
        var removed: String? = null

        override fun observeInstalled(): Flow<List<InstalledSkill>> = installed

        override suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport {
            preparedSource = source
            prepareFailure?.let { throw it }
            return prepared
        }

        override suspend fun commitImport(preparedId: String, allowReplace: Boolean) {
            committed = preparedId to allowReplace
        }

        override suspend fun discardImport(preparedId: String) {
            discarded = preparedId
        }

        override suspend fun remove(name: String) {
            removed = name
        }
    }
}
