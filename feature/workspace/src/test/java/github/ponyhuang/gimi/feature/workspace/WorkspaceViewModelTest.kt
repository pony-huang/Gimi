package github.ponyhuang.gimi.feature.workspace

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.repository.WorkspaceRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkspaceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val files = listOf(
        WorkspaceFile("a.jpg", "/ws/a.jpg", 10L, 200L, "image/jpeg"),
        WorkspaceFile("b.pdf", "/ws/b.pdf", 20L, 100L, "application/pdf"),
    )

    private class FakeWorkspaceRepository(
        private val files: List<WorkspaceFile>,
    ) : WorkspaceRepository {
        val deletedPaths = mutableListOf<String>()
        var failDeleteFor: Set<String> = emptySet()

        override suspend fun list(): List<WorkspaceFile> =
            files.filter { it.path !in deletedPaths }

        override suspend fun totalBytes(): Long =
            files.filter { it.path !in deletedPaths }.sumOf { it.sizeBytes }

        override suspend fun delete(target: WorkspaceFile): Boolean {
            // 失败的删除不改变文件系统：文件仍会出现在后续 list() 中。
            val success = target.path !in failDeleteFor
            if (success) deletedPaths += target.path
            return success
        }
    }

    @Test
    fun initialLoadPopulatesFilesAndTotalBytes() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = WorkspaceViewModel(FakeWorkspaceRepository(files))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(files, state.files)
        assertEquals(30L, state.totalBytes)
        assertTrue(!state.isLoading && !state.loadFailed && !state.isSelecting)
    }

    @Test
    fun toggleSelectEntersSelectionAndExitClears() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = WorkspaceViewModel(FakeWorkspaceRepository(files))
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.ToggleSelected(files[0].path))
        assertTrue(viewModel.uiState.value.isSelecting)
        assertEquals(setOf(files[0].path), viewModel.uiState.value.selectedPaths)

        viewModel.onAction(WorkspaceAction.ExitSelection)
        assertTrue(!viewModel.uiState.value.isSelecting)
    }

    @Test
    fun confirmedDeleteRemovesEachSelectedFileAndReportsResult() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeWorkspaceRepository(files)
        val viewModel = WorkspaceViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.ToggleSelected(files[0].path))
        viewModel.onAction(WorkspaceAction.ToggleSelected(files[1].path))
        viewModel.onAction(WorkspaceAction.RequestDelete)
        assertEquals(files.map { it.path }.toSet(), viewModel.uiState.value.pendingDeletePaths)
        viewModel.onAction(WorkspaceAction.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(files.map { it.path }, repository.deletedPaths)
        assertEquals(2, viewModel.uiState.value.deleteResult?.deletedCount)
        assertEquals(0, viewModel.uiState.value.deleteResult?.failedCount)
        assertTrue(!viewModel.uiState.value.isSelecting)
        assertTrue(viewModel.uiState.value.pendingDeletePaths.isEmpty())
        assertEquals(0L, viewModel.uiState.value.totalBytes)
    }

    @Test
    fun deleteFailureIsCountedWithoutInterruptingTheRest() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeWorkspaceRepository(files).apply {
            failDeleteFor = setOf(files[0].path)
        }
        val viewModel = WorkspaceViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.ToggleSelected(files[0].path))
        viewModel.onAction(WorkspaceAction.ToggleSelected(files[1].path))
        viewModel.onAction(WorkspaceAction.RequestDelete)
        viewModel.onAction(WorkspaceAction.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(1, repository.deletedPaths.size)
        assertEquals(1, viewModel.uiState.value.deleteResult?.deletedCount)
        assertEquals(1, viewModel.uiState.value.deleteResult?.failedCount)
        // 失败项保留在列表中，成功项消失。
        assertEquals(listOf(files[0]), viewModel.uiState.value.files)
    }

    @Test
    fun clearingFeedbackConsumesTheOneShotResult() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = WorkspaceViewModel(FakeWorkspaceRepository(files))
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.ToggleSelected(files[0].path))
        viewModel.onAction(WorkspaceAction.RequestDelete)
        viewModel.onAction(WorkspaceAction.ConfirmDelete)
        advanceUntilIdle()
        viewModel.onAction(WorkspaceAction.ClearDeleteFeedback)

        assertNull(viewModel.uiState.value.deleteResult)
    }
}
