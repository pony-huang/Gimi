package github.ponyhuang.gimi.feature.workfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ClearReclaimableStorageUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.LoadAppStorageSummaryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.ReauthorizeWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.RefreshWorkDirectoryAccessUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import github.ponyhuang.gimi.domain.workfiles.usecase.SetWorkDirectoryEnabledUseCase
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WorkFilesSettingsViewModel @Inject constructor(
    observeDirectories: ObserveWorkDirectoriesUseCase,
    private val addDirectory: AddWorkDirectoryUseCase,
    private val removeDirectory: RemoveWorkDirectoryUseCase,
    private val setDirectoryEnabled: SetWorkDirectoryEnabledUseCase,
    private val reauthorizeDirectory: ReauthorizeWorkDirectoryUseCase,
    private val refreshDirectoryAccess: RefreshWorkDirectoryAccessUseCase,
    private val loadStorageSummary: LoadAppStorageSummaryUseCase,
    private val clearReclaimableStorage: ClearReclaimableStorageUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WorkFilesSettingsUiState())
    val uiState: StateFlow<WorkFilesSettingsUiState> = mutableUiState.asStateFlow()
    private var nextPickerRequestId = 0
    private var reauthorizingDirectoryId: String? = null

    init {
        viewModelScope.launch {
            observeDirectories().collect { directories ->
                mutableUiState.update { it.copy(directories = directories) }
            }
        }
        viewModelScope.launch { refreshDirectoryAccess() }
        refreshStorage()
    }

    fun onAction(action: WorkFilesSettingsAction) {
        when (action) {
            WorkFilesSettingsAction.RequestAddDirectory -> requestPicker(reauthorizingId = null)
            is WorkFilesSettingsAction.RequestReauthorizeDirectory ->
                requestPicker(reauthorizingId = action.id)
            is WorkFilesSettingsAction.DirectorySelected -> handleDirectorySelection(action.uri)
            is WorkFilesSettingsAction.RemoveDirectory ->
                runDirectoryOperation { removeDirectory(action.id) }
            is WorkFilesSettingsAction.SetDirectoryEnabled ->
                runDirectoryOperation { setDirectoryEnabled(action.id, action.enabled) }
            is WorkFilesSettingsAction.DirectoryPickerHandled -> {
                if (mutableUiState.value.directoryPickerRequestId == action.requestId) {
                    mutableUiState.update { it.copy(directoryPickerRequestId = null) }
                }
            }
            WorkFilesSettingsAction.RefreshStorage -> refreshStorage()
            WorkFilesSettingsAction.ClearReclaimableStorage -> clearStorage()
            WorkFilesSettingsAction.ClearOperationFeedback ->
                mutableUiState.update { it.copy(operationError = null, lastClearResult = null) }
        }
    }

    private fun requestPicker(reauthorizingId: String?) {
        reauthorizingDirectoryId = reauthorizingId
        mutableUiState.update {
            it.copy(directoryPickerRequestId = ++nextPickerRequestId, operationError = null)
        }
    }

    private fun handleDirectorySelection(uri: String?) {
        val reauthorizingId = reauthorizingDirectoryId
        reauthorizingDirectoryId = null
        if (uri.isNullOrBlank()) return
        if (reauthorizingId == null) {
            runDirectoryOperation { addDirectory(uri) }
        } else {
            runDirectoryOperation { reauthorizeDirectory(reauthorizingId, uri) }
        }
    }

    private fun runDirectoryOperation(
        operation: suspend () -> WorkDirectoryOperationResult,
    ) {
        viewModelScope.launch {
            val error = operation() as? WorkDirectoryOperationResult.Failure
            mutableUiState.update { it.copy(operationError = error) }
            if (error == null) refreshStorage()
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isStorageLoading = true, storageLoadFailed = false) }
            try {
                val summary = loadStorageSummary()
                mutableUiState.update {
                    it.copy(
                        storageSummary = summary,
                        isStorageLoading = false,
                        storageLoadFailed = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(isStorageLoading = false, storageLoadFailed = true)
                }
            }
        }
    }

    private fun clearStorage() {
        if (mutableUiState.value.isClearingStorage) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isClearingStorage = true, lastClearResult = null) }
            try {
                val result = clearReclaimableStorage()
                mutableUiState.update { it.copy(isClearingStorage = false, lastClearResult = result) }
                refreshStorage()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(
                        isClearingStorage = false,
                        lastClearResult = github.ponyhuang.gimi.domain.workfiles.model.StorageClearSummary(
                            clearedDirectoryCount = 0,
                            failedDirectoryCount = 1,
                            reclaimedBytes = 0L,
                        ),
                    )
                }
            }
        }
    }
}
