package github.ponyhuang.gimi.feature.workfiles

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkFilesSettingsViewModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observedDirectoriesAreExposedAsSingleUiState() = runTest {
        val directory = WorkDirectory(
            uri = "content://documents/tree/work",
            displayName = "work",
            authority = "documents",
        )
        val repository = repository(listOf(directory))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.directories.isEmpty()) state = awaitItem()

            assertEquals(listOf(directory), state.directories)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickerSelectionAndRemovalDelegateEncodedUri() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val uri = "content://documents/tree/work"

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(WorkFilesSettingsAction.RequestAddDirectory)
            var state = awaitItem()
            while (state.directoryPickerRequestId == null) state = awaitItem()

            val requestId = requireNotNull(state.directoryPickerRequestId)
            viewModel.onAction(WorkFilesSettingsAction.DirectoryPickerHandled(requestId))
            viewModel.onAction(WorkFilesSettingsAction.DirectorySelected(uri))
            viewModel.onAction(WorkFilesSettingsAction.RemoveDirectory(uri))
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.addDirectory(uri) }
            coVerify(exactly = 1) { repository.removeDirectory(uri) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repositoryFailureIsExposedInUiState() = runTest {
        val repository = repository()
        coEvery { repository.addDirectory(any()) } returns
            WorkDirectoryOperationResult.Failure.PermissionDenied
        val viewModel = viewModel(repository)

        viewModel.onAction(WorkFilesSettingsAction.DirectorySelected("content://denied"))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.operationError == null) state = awaitItem()
            assertEquals(
                WorkDirectoryOperationResult.Failure.PermissionDenied,
                state.operationError,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(repository: WorkDirectoryRepository) = WorkFilesSettingsViewModel(
        observeDirectories = ObserveWorkDirectoriesUseCase(repository),
        addDirectory = AddWorkDirectoryUseCase(repository),
        removeDirectory = RemoveWorkDirectoryUseCase(repository),
    )

    private fun repository(
        directories: List<WorkDirectory> = emptyList(),
    ): WorkDirectoryRepository = mockk(relaxed = true) {
        every { observeDirectories() } returns MutableStateFlow(directories)
        coEvery { addDirectory(any()) } returns WorkDirectoryOperationResult.Success
        coEvery { removeDirectory(any()) } returns WorkDirectoryOperationResult.Success
    }
}
