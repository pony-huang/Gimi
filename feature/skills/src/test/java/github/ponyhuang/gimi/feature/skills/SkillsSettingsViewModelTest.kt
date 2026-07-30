package github.ponyhuang.gimi.feature.skills

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import github.ponyhuang.gimi.domain.skills.usecase.CommitSkillImportUseCase
import github.ponyhuang.gimi.domain.skills.usecase.DiscardSkillImportUseCase
import github.ponyhuang.gimi.domain.skills.usecase.ObserveInstalledSkillsUseCase
import github.ponyhuang.gimi.domain.skills.usecase.PrepareSkillImportUseCase
import github.ponyhuang.gimi.domain.skills.usecase.RemoveSkillUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun urlImportInstallsPreparedSkillAndPublishesNotice() = runTest {
        val repository = FakeSkillRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(SkillsSettingsAction.OpenUrlDialog)
        viewModel.onAction(SkillsSettingsAction.UrlChanged("https://example.com/skill.zip"))
        viewModel.onAction(SkillsSettingsAction.SubmitUrl)
        advanceUntilIdle()

        assertEquals(
            SkillImportSource.Url("https://example.com/skill.zip"),
            repository.lastPreparedSource,
        )
        assertEquals("prepared", repository.lastCommittedId)
        assertFalse(repository.lastAllowReplace)
        assertEquals(SkillsNotice.Installed("demo"), viewModel.uiState.value.notice)
    }

    @Test
    fun duplicateWaitsForExplicitReplacementConfirmation() = runTest {
        val repository = FakeSkillRepository(replacesExisting = true)
        val viewModel = viewModel(repository)

        viewModel.onAction(
            SkillsSettingsAction.LocalArchiveSelected("content://skills/demo.zip"),
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingReplacement)
        assertNull(repository.lastCommittedId)

        viewModel.onAction(SkillsSettingsAction.ConfirmReplacement)
        advanceUntilIdle()

        assertEquals("prepared", repository.lastCommittedId)
        assertTrue(repository.lastAllowReplace)
        assertNull(viewModel.uiState.value.pendingReplacement)
    }

    @Test
    fun cancellingReplacementDiscardsPreparedImport() = runTest {
        val repository = FakeSkillRepository(replacesExisting = true)
        val viewModel = viewModel(repository)
        viewModel.onAction(
            SkillsSettingsAction.LocalArchiveSelected("content://skills/demo.zip"),
        )
        advanceUntilIdle()

        viewModel.onAction(SkillsSettingsAction.CancelReplacement)
        advanceUntilIdle()

        assertEquals("prepared", repository.lastDiscardedId)
        assertNull(viewModel.uiState.value.pendingReplacement)
    }

    @Test
    fun removalRequiresConfirmation() = runTest {
        val repository = FakeSkillRepository(
            installed = listOf(InstalledSkill("demo", "Demo")),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.skills.isEmpty()) state = awaitItem()
            viewModel.onAction(SkillsSettingsAction.RequestRemove(state.skills.single()))
            assertEquals("demo", awaitItem().pendingRemoval?.name)
            assertNull(repository.lastRemovedName)

            viewModel.onAction(SkillsSettingsAction.ConfirmRemoval)
            advanceUntilIdle()
            assertEquals("demo", repository.lastRemovedName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(repository: SkillRepository) = SkillsSettingsViewModel(
        observeInstalled = ObserveInstalledSkillsUseCase(repository),
        prepareImport = PrepareSkillImportUseCase(repository),
        commitImport = CommitSkillImportUseCase(repository),
        discardImport = DiscardSkillImportUseCase(repository),
        removeSkill = RemoveSkillUseCase(repository),
    )

    private class FakeSkillRepository(
        installed: List<InstalledSkill> = emptyList(),
        private val replacesExisting: Boolean = false,
    ) : SkillRepository {
        private val skills = MutableStateFlow(installed)
        var lastPreparedSource: SkillImportSource? = null
        var lastCommittedId: String? = null
        var lastAllowReplace: Boolean = false
        var lastDiscardedId: String? = null
        var lastRemovedName: String? = null

        override fun observeInstalled(): Flow<List<InstalledSkill>> = skills

        override suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport {
            lastPreparedSource = source
            return PreparedSkillImport(
                id = "prepared",
                name = "demo",
                description = "Demo",
                replacesExisting = replacesExisting,
            )
        }

        override suspend fun commitImport(preparedId: String, allowReplace: Boolean) {
            lastCommittedId = preparedId
            lastAllowReplace = allowReplace
            skills.value = listOf(InstalledSkill("demo", "Demo"))
        }

        override suspend fun discardImport(preparedId: String) {
            lastDiscardedId = preparedId
        }

        override suspend fun remove(name: String) {
            lastRemovedName = name
            skills.value = skills.value.filterNot { it.name == name }
        }
    }
}
