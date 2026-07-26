package github.ponyhuang.asssistantai.feature.workfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import github.ponyhuang.asssistantai.domain.workfiles.repository.WorkDirectoryOperationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WorkFilesSettingsViewModel @Inject constructor(
    observeDirectories: ObserveWorkDirectoriesUseCase,
    private val addDirectory: AddWorkDirectoryUseCase,
    private val removeDirectory: RemoveWorkDirectoryUseCase,
) : ViewModel() {
    private val pickerRequestId = MutableStateFlow<Int?>(null)
    private val operationError = MutableStateFlow<WorkDirectoryOperationResult.Failure?>(null)
    private var nextPickerRequestId = 0

    val uiState = combine(
        observeDirectories(),
        pickerRequestId,
        operationError,
    ) { directories, requestId, error ->
        WorkFilesSettingsUiState(
            directories = directories,
            directoryPickerRequestId = requestId,
            operationError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkFilesSettingsUiState(),
    )

    fun onAction(action: WorkFilesSettingsAction) {
        when (action) {
            WorkFilesSettingsAction.RequestAddDirectory -> {
                pickerRequestId.value = ++nextPickerRequestId
            }
            is WorkFilesSettingsAction.DirectorySelected -> {
                if (action.uri.isNotBlank()) {
                    runOperation { addDirectory(action.uri) }
                }
            }
            is WorkFilesSettingsAction.RemoveDirectory -> {
                runOperation { removeDirectory(action.uri) }
            }
            is WorkFilesSettingsAction.DirectoryPickerHandled -> {
                if (pickerRequestId.value == action.requestId) pickerRequestId.value = null
            }
        }
    }

    private fun runOperation(
        operation: suspend () -> WorkDirectoryOperationResult,
    ) {
        viewModelScope.launch {
            operationError.value = operation() as? WorkDirectoryOperationResult.Failure
        }
    }
}
