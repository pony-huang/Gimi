package github.ponyhuang.gimi.feature.memory

import github.ponyhuang.gimi.domain.memory.model.ManagedMemory
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback

/** 云端记忆历史页的展示状态。 */
data class MemoryHistoryUiState(
    /** 已加载的云端记忆。 */
    val memories: List<ManagedMemory> = emptyList(),
    /** 首次加载或刷新是否进行中。 */
    val refreshing: Boolean = false,
    /** 是否正在加载下一页。 */
    val loadingNextPage: Boolean = false,
    /** 列表是否仍有下一页。 */
    val hasNextPage: Boolean = false,
    /** 当前待确认删除的记忆。 */
    val pendingDelete: ManagedMemory? = null,
    /** 正在提交操作的记忆标识。 */
    val operatingMemoryId: String? = null,
    /** 已展开以显示完整文本和操作按钮的记忆标识。 */
    val expandedMemoryIds: Set<String> = emptySet(),
) {
    /** 是否应显示删除确认对话框。 */
    val showDeleteConfirmation: Boolean get() = pendingDelete != null
}

/** 云端记忆历史页的用户操作。 */
sealed interface MemoryHistoryAction {
    /** 重新加载第一页。 */
    data object Refresh : MemoryHistoryAction

    /** 加载下一页。 */
    data object LoadNextPage : MemoryHistoryAction

    /** 展开或收起一条记忆的完整内容和操作区。 */
    data class ToggleExpanded(val memoryId: String) : MemoryHistoryAction

    /** 请求删除一条记忆。 */
    data class RequestDelete(val memory: ManagedMemory) : MemoryHistoryAction

    /** 取消删除确认。 */
    data object DismissDelete : MemoryHistoryAction

    /** 确认删除当前待删记忆。 */
    data object ConfirmDelete : MemoryHistoryAction

    /** 提交单条记忆反馈。 */
    data class SubmitFeedback(
        val memory: ManagedMemory,
        val feedback: ManagedMemoryFeedback,
        val reason: String?,
    ) : MemoryHistoryAction
}

/** 云端记忆历史页一次性的用户提示。 */
sealed interface MemoryHistoryEffect {
    /** 删除已完成。 */
    data object Deleted : MemoryHistoryEffect

    /** 反馈已提交。 */
    data object FeedbackSubmitted : MemoryHistoryEffect

    /** 远端请求失败。 */
    data object OperationFailed : MemoryHistoryEffect
}
