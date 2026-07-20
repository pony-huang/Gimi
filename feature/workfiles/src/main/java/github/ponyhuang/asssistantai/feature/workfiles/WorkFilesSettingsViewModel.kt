package github.ponyhuang.asssistantai.feature.workfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.workfiles.usecase.AddWorkDirectoryUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.ObserveWorkDirectoriesUseCase
import github.ponyhuang.asssistantai.domain.workfiles.usecase.RemoveWorkDirectoryUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WorkFilesSettingsViewModel @Inject constructor(
    observeDirectories: ObserveWorkDirectoriesUseCase,
    private val addDirectory: AddWorkDirectoryUseCase,
    private val removeDirectory: RemoveWorkDirectoryUseCase,
) : ViewModel() {
    private val pickerRequestId = MutableStateFlow<Int?>(null)
    private var nextPickerRequestId = 0

    val uiState = combine(observeDirectories(), pickerRequestId) { directories, requestId ->
        WorkFilesSettingsUiState(
            directories = directories,
            directoryPickerRequestId = requestId,
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
                if (action.uri.isNotBlank()) addDirectory(action.uri)
            }
            is WorkFilesSettingsAction.RemoveDirectory -> removeDirectory(action.uri)
            is WorkFilesSettingsAction.DirectoryPickerHandled -> {
                if (pickerRequestId.value == action.requestId) pickerRequestId.value = null
            }
        }
    }
}
