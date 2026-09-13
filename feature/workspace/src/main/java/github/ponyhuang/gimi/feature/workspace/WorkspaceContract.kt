package github.ponyhuang.gimi.feature.workspace

import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile

/**
 * 工作区管理页的一次性状态快照。
 *
 * 多选态由 [selectedPaths] 非空隐式表达；删除走两段式（[WorkspaceAction.RequestDelete]
 * 请求确认 → [WorkspaceAction.ConfirmDelete] 执行），避免误触不可恢复的删除。
 *
 * @property files 工作区文件快照，按最后修改时间倒序；文件系统是真源，仅在此刻有效。
 * @property totalBytes 工作区当前总占用（字节）。
 * @property isLoading 首次加载进行中。
 * @property loadFailed 列表加载失败，UI 提供重试入口。
 * @property selectedPaths 处于多选态的文件路径集合；为空即浏览态。
 * @property pendingDeletePaths 已请求删除、等待用户在确认对话框中拍板的路径集合。
 * @property isDeleting 批量删除执行中，删除按钮防重入。
 * @property deleteResult 最近一次批量删除的结果；非空时由 Route 以 Toast 反馈并消费。
 */
data class WorkspaceUiState(
    val files: List<WorkspaceFile> = emptyList(),
    val totalBytes: Long = 0L,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeletePaths: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val deleteResult: WorkspaceDeleteResult? = null,
) {
    /** 当前是否处于多选态。 */
    val isSelecting: Boolean get() = selectedPaths.isNotEmpty()
}

/** 一次批量删除的结果，用于聚合反馈（逐项失败不中断其余删除）。 */
data class WorkspaceDeleteResult(
    val deletedCount: Int,
    val failedCount: Int,
)

/** 工作区管理页的用户意图。 */
sealed interface WorkspaceAction {
    /** 列表加载失败后重试。 */
    data object RetryLoad : WorkspaceAction

    /** 多选态下切换某个文件的选中状态；浏览态下长按以该文件为起点进入多选。 */
    data class ToggleSelected(val path: String) : WorkspaceAction

    /** 退出多选态，清空所有勾选。 */
    data object ExitSelection : WorkspaceAction

    /** 请求删除当前勾选的文件，弹出确认对话框。 */
    data object RequestDelete : WorkspaceAction

    /** 在确认对话框中确认删除。 */
    data object ConfirmDelete : WorkspaceAction

    /** 在确认对话框中取消，不执行删除。 */
    data object DismissDeleteDialog : WorkspaceAction

    /** 已展示删除结果反馈，清除该一次性状态。 */
    data object ClearDeleteFeedback : WorkspaceAction
}
