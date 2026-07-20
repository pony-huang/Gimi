package github.ponyhuang.asssistantai.feature.workfiles

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.workfiles.model.WorkDirectory
import github.ponyhuang.asssistantai.domain.workfiles.repository.WorkDirectoryRepository
import github.ponyhuang.asssistantai.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

            verify(exactly = 1) { repository.addDirectory(uri) }
            verify(exactly = 1) { repository.removeDirectory(uri) }
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
        every { addDirectory(any()) } returns true
    }
}
