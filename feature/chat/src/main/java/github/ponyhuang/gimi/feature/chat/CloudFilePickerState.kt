package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChoice
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory

/** 同一抽屉的临时选择，搜索不丢失勾选；返回或连接变化时全部放弃。 */
data class CloudFilePickerState(
    val localRevision: Long = 0,
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val serviceName: String = "",
    val choices: List<CloudFileChoice> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val query: String = "",
) {
    val visibleChoices: List<CloudFileChoice> get() = choices.filter { it.file.displayName.contains(query.trim(), ignoreCase = true) }
    val selectedDrafts get() = choices.filter { it.file.id in selectedIds }.mapNotNull { it.toDraft() }
    fun canToggle(choice: CloudFileChoice): Boolean {
        if (!choice.isSelectable) return false
        if (choice.file.id in selectedIds) return true
        if (selectedIds.size >= 3) return false
        val category = AttachmentCategory.from(choice.file.mimeType, choice.file.displayName) ?: return false
        return selectedDrafts.all { it.category == category }
    }
}

/** 选择页意图只影响临时勾选；确认后的草稿由 composer 所有者更新。 */
sealed interface CloudFilePickerAction {
    /** 图片行进入可见区域才请求原文件，避免打开列表就下载所有附件。 */
    data class LoadLocal(val reference: github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference) : CloudFilePickerAction
    /** 打开或重新载入当前连接的文件。 */
    data object Open : CloudFilePickerAction
    /** 关闭后不保留未确认选择。 */
    data object Close : CloudFilePickerAction
    /** 搜索不会清除已选项。 */
    data class QueryChanged(val value: String) : CloudFilePickerAction
    /** 只允许选择当前列表中可用的文件。 */
    data class Toggle(val id: String) : CloudFilePickerAction
}
