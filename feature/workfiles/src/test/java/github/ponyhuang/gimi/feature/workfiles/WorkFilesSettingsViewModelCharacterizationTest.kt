package github.ponyhuang.gimi.feature.workfiles

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.workfiles.model.AppStorageSummary
import github.ponyhuang.gimi.domain.workfiles.model.StorageClearSummary
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.repository.AppStorageManagementRepository
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import github.ponyhuang.gimi.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ClearReclaimableStorageUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.LoadAppStorageSummaryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ReauthorizeWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.RefreshWorkDirectoryAccessUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.SetWorkDirectoryEnabledUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
    fun initialStateLoadsDirectoriesAccessHealthAndStorageSummary() = runTest {
        val directory = directory("work")
        val directoryRepository = directoryRepository(listOf(directory))
        val storageRepository = storageRepository()

        val viewModel = viewModel(directoryRepository, storageRepository)
        advanceUntilIdle()

        assertEquals(listOf(directory), viewModel.uiState.value.directories)
        assertEquals(STORAGE_SUMMARY, viewModel.uiState.value.storageSummary)
        coVerify(exactly = 1) { directoryRepository.refreshAccess() }
        coVerify(exactly = 1) { storageRepository.loadSummary() }
    }

    @Test
    fun pickerSelectionAndRemovalDelegateStableDirectoryId() = runTest {
        val repository = directoryRepository()
        val viewModel = viewModel(repository)
        val uri = "content://documents/tree/work"

        viewModel.onAction(WorkFilesSettingsAction.RequestAddDirectory)
        val requestId = requireNotNull(viewModel.uiState.value.directoryPickerRequestId)
        viewModel.onAction(WorkFilesSettingsAction.DirectoryPickerHandled(requestId))
        viewModel.onAction(WorkFilesSettingsAction.DirectorySelected(uri))
        viewModel.onAction(WorkFilesSettingsAction.RemoveDirectory("directory-id"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.addDirectory(uri) }
        coVerify(exactly = 1) { repository.removeDirectory("directory-id") }
    }

    @Test
    fun enableActionDelegatesStableIdAndRequestedState() = runTest {
        val repository = directoryRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(WorkFilesSettingsAction.SetDirectoryEnabled("directory-id", false))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabled("directory-id", false) }
    }

    @Test
    fun reauthorizePickerRoutesSelectionToExistingDirectory() = runTest {
        val repository = directoryRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(WorkFilesSettingsAction.RequestReauthorizeDirectory("directory-id"))
        viewModel.onAction(WorkFilesSettingsAction.DirectorySelected("content://documents/tree/new"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.reauthorize("directory-id", "content://documents/tree/new")
        }
        coVerify(exactly = 0) { repository.addDirectory(any()) }
    }

    @Test
    fun clearActionPublishesResultAndReloadsStorageSummary() = runTest {
        val storageRepository = storageRepository()
        coEvery { storageRepository.clearReclaimable() } returns StorageClearSummary(
            clearedDirectoryCount = 3,
            failedDirectoryCount = 1,
            reclaimedBytes = 72L,
        )
        val viewModel = viewModel(directoryRepository(), storageRepository)
        advanceUntilIdle()

        viewModel.onAction(WorkFilesSettingsAction.ClearReclaimableStorage)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.lastClearResult?.clearedDirectoryCount)
        assertEquals(1, viewModel.uiState.value.lastClearResult?.failedDirectoryCount)
        coVerify(exactly = 2) { storageRepository.loadSummary() }
    }

    @Test
    fun repositoryFailureIsExposedInUiState() = runTest {
        val repository = directoryRepository()
        coEvery { repository.addDirectory(any()) } returns
            WorkDirectoryOperationResult.Failure.PermissionDenied
        val viewModel = viewModel(repository)

        viewModel.onAction(WorkFilesSettingsAction.DirectorySelected("content://denied"))
        advanceUntilIdle()

        assertEquals(
            WorkDirectoryOperationResult.Failure.PermissionDenied,
            viewModel.uiState.value.operationError,
        )
    }

    private fun viewModel(
        directoryRepository: WorkDirectoryRepository,
        storageRepository: AppStorageManagementRepository = storageRepository(),
    ) = WorkFilesSettingsViewModel(
        observeDirectories = ObserveWorkDirectoriesUseCase(directoryRepository),
        addDirectory = AddWorkDirectoryUseCase(directoryRepository),
        removeDirectory = RemoveWorkDirectoryUseCase(directoryRepository),
        setDirectoryEnabled = SetWorkDirectoryEnabledUseCase(directoryRepository),
        reauthorizeDirectory = ReauthorizeWorkDirectoryUseCase(directoryRepository),
        refreshDirectoryAccess = RefreshWorkDirectoryAccessUseCase(directoryRepository),
        loadStorageSummary = LoadAppStorageSummaryUseCase(storageRepository),
        clearReclaimableStorage = ClearReclaimableStorageUseCase(storageRepository),
    )

    private fun directoryRepository(
        directories: List<WorkDirectory> = emptyList(),
    ): WorkDirectoryRepository = mockk(relaxed = true) {
        every { observeDirectories() } returns MutableStateFlow(directories)
        coEvery { addDirectory(any()) } returns WorkDirectoryOperationResult.Success
        coEvery { removeDirectory(any()) } returns WorkDirectoryOperationResult.Success
        coEvery { setEnabled(any(), any()) } returns WorkDirectoryOperationResult.Success
        coEvery { reauthorize(any(), any()) } returns WorkDirectoryOperationResult.Success
    }

    private fun storageRepository(): AppStorageManagementRepository = mockk {
        coEvery { loadSummary() } returns STORAGE_SUMMARY
        coEvery { clearReclaimable() } returns StorageClearSummary(0, 0, 0L)
    }

    private fun directory(id: String) = WorkDirectory(
        id = id,
        treeUri = "content://documents/tree/$id",
        displayName = id,
        authority = "documents",
        enabled = true,
        accessStatus = WorkDirectoryAccessStatus.AVAILABLE,
        addedAtEpochMillis = 1L,
    )

    private companion object {
        val STORAGE_SUMMARY = AppStorageSummary(
            totalBytes = 286L,
            persistentBytes = 214L,
            cacheBytes = 48L,
            temporaryBytes = 24L,
            unreadableDirectoryCount = 0,
        )
    }
}
