package github.ponyhuang.asssistantai.feature.workfiles

import github.ponyhuang.asssistantai.domain.workfiles.model.WorkDirectory
import github.ponyhuang.asssistantai.domain.workfiles.repository.WorkDirectoryOperationResult

data class WorkFilesSettingsUiState(
    val directories: List<WorkDirectory> = emptyList(),
    val directoryPickerRequestId: Int? = null,
    val operationError: WorkDirectoryOperationResult.Failure? = null,
)

sealed interface WorkFilesSettingsAction {
    data object RequestAddDirectory : WorkFilesSettingsAction
    data class DirectorySelected(val uri: String) : WorkFilesSettingsAction
    data class RemoveDirectory(val uri: String) : WorkFilesSettingsAction
    data class DirectoryPickerHandled(val requestId: Int) : WorkFilesSettingsAction
}
