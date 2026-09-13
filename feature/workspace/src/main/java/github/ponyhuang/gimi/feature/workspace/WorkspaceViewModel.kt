package github.ponyhuang.gimi.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.repository.WorkspaceRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 工作区管理页 ViewModel：只依赖 [WorkspaceRepository] 域契约。
 *
 * 文件系统是真源，列表不做本地缓存——每次进入、每次删除后都重新拉取快照。删除必须
 * 经用户在确认对话框中拍板后执行；逐项失败聚合进 [WorkspaceDeleteResult]，不中断其余项。
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun onAction(action: WorkspaceAction) {
        when (action) {
            WorkspaceAction.RetryLoad -> refresh()
            is WorkspaceAction.ToggleSelected -> toggleSelected(action.path)
            WorkspaceAction.ExitSelection -> mutableUiState.update {
                it.copy(selectedPaths = emptySet())
            }
            WorkspaceAction.RequestDelete -> mutableUiState.update {
                it.copy(pendingDeletePaths = it.selectedPaths)
            }
            WorkspaceAction.ConfirmDelete -> confirmDelete()
            WorkspaceAction.DismissDeleteDialog -> mutableUiState.update {
                it.copy(pendingDeletePaths = emptySet())
            }
            WorkspaceAction.ClearDeleteFeedback -> mutableUiState.update {
                it.copy(deleteResult = null)
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, loadFailed = false) }
            try {
                val files = workspaceRepository.list()
                val totalBytes = workspaceRepository.totalBytes()
                mutableUiState.update {
                    it.copy(
                        files = files,
                        totalBytes = totalBytes,
                        isLoading = false,
                        loadFailed = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(files = emptyList(), totalBytes = 0L, isLoading = false, loadFailed = true)
                }
            }
        }
    }

    private fun toggleSelected(path: String) {
        mutableUiState.update { state ->
            state.copy(
                selectedPaths = if (path in state.selectedPaths) {
                    state.selectedPaths - path
                } else {
                    state.selectedPaths + path
                },
            )
        }
    }

    private fun confirmDelete() {
        val state = mutableUiState.value
        if (state.isDeleting || state.pendingDeletePaths.isEmpty()) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isDeleting = true) }
            var deletedCount = 0
            var failedCount = 0
            state.pendingDeletePaths.forEach { path ->
                val target = state.files.firstOrNull { it.path == path }
                    ?: fallbackFileFor(path)
                if (workspaceRepository.delete(target)) deletedCount++ else failedCount++
            }
            mutableUiState.update {
                it.copy(
                    isDeleting = false,
                    selectedPaths = emptySet(),
                    pendingDeletePaths = emptySet(),
                    deleteResult = WorkspaceDeleteResult(
                        deletedCount = deletedCount,
                        failedCount = failedCount,
                    ),
                )
            }
            refresh()
        }
    }

    /** 列表快照已过期时按路径兜底构造；仓储层实现按路径守卫与删除。 */
    private fun fallbackFileFor(path: String): WorkspaceFile = WorkspaceFile(
        name = path.substringAfterLast('/'),
        path = path,
        sizeBytes = 0L,
        lastModifiedMillis = 0L,
    )
}
