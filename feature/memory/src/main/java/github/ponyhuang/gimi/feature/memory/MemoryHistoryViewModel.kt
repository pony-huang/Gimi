package github.ponyhuang.gimi.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.memory.repository.Mem0MemoryManagementRepository
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 协调 Mem0 云端记忆列表、删除确认和用户反馈的页面状态。 */
@HiltViewModel
class MemoryHistoryViewModel @Inject constructor(
    private val repository: Mem0MemoryManagementRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MemoryHistoryUiState())
    val uiState = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<MemoryHistoryEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    private var nextPage = 1

    init {
        loadFirstPage()
    }

    fun onAction(action: MemoryHistoryAction) {
        when (action) {
            MemoryHistoryAction.Refresh -> loadFirstPage()
            MemoryHistoryAction.LoadNextPage -> loadNextPage()
            is MemoryHistoryAction.ToggleExpanded -> mutableUiState.update { state ->
                state.copy(
                    expandedMemoryIds = state.expandedMemoryIds.toggle(action.memoryId),
                )
            }
            is MemoryHistoryAction.RequestDelete -> mutableUiState.update { it.copy(pendingDelete = action.memory) }
            MemoryHistoryAction.DismissDelete -> mutableUiState.update { it.copy(pendingDelete = null) }
            MemoryHistoryAction.ConfirmDelete -> deletePendingMemory()
            is MemoryHistoryAction.SubmitFeedback -> submitFeedback(action)
        }
    }

    private fun loadFirstPage() {
        if (mutableUiState.value.refreshing) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(refreshing = true) }
            try {
                val page = repository.loadPage(page = 1, pageSize = PAGE_SIZE)
                nextPage = 2
                mutableUiState.update {
                    it.copy(memories = page.memories, hasNextPage = page.hasNextPage, refreshing = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(refreshing = false) }
                mutableEffects.emit(MemoryHistoryEffect.OperationFailed)
            }
        }
    }

    private fun loadNextPage() {
        val state = mutableUiState.value
        if (state.refreshing || state.loadingNextPage || !state.hasNextPage) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(loadingNextPage = true) }
            try {
                val page = repository.loadPage(page = nextPage, pageSize = PAGE_SIZE)
                nextPage += 1
                mutableUiState.update {
                    it.copy(
                        // Mem0 分页边界可能返回重叠记录；Compose 列表的 key 必须全局唯一。
                        memories = (it.memories + page.memories).distinctBy { memory -> memory.id },
                        hasNextPage = page.hasNextPage,
                        loadingNextPage = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(loadingNextPage = false) }
                mutableEffects.emit(MemoryHistoryEffect.OperationFailed)
            }
        }
    }

    private fun deletePendingMemory() {
        val memory = mutableUiState.value.pendingDelete ?: return
        viewModelScope.launch {
            mutableUiState.update { it.copy(operatingMemoryId = memory.id) }
            try {
                repository.delete(memory.id)
                mutableUiState.update {
                    it.copy(
                        memories = it.memories.filterNot { item -> item.id == memory.id },
                        pendingDelete = null,
                        operatingMemoryId = null,
                    )
                }
                mutableEffects.emit(MemoryHistoryEffect.Deleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(operatingMemoryId = null) }
                mutableEffects.emit(MemoryHistoryEffect.OperationFailed)
            }
        }
    }

    private fun submitFeedback(action: MemoryHistoryAction.SubmitFeedback) {
        if (mutableUiState.value.operatingMemoryId != null) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(operatingMemoryId = action.memory.id) }
            try {
                repository.submitFeedback(action.memory.id, action.feedback, action.reason)
                mutableUiState.update { it.copy(operatingMemoryId = null) }
                mutableEffects.emit(MemoryHistoryEffect.FeedbackSubmitted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(operatingMemoryId = null) }
                mutableEffects.emit(MemoryHistoryEffect.OperationFailed)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
