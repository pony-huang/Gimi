package github.ponyhuang.gimi.feature.modelsettings.local

import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelState

/** 本地模型页面的单一快照，pendingRemoval 为等待用户确认删除的版本。 */
data class LocalModelUiState(
    val loading: Boolean = true,
    val models: List<LocalModelState> = emptyList(),
    val pendingRemoval: LocalModelState? = null,
    val mutationBlocked: Boolean = false,
    val operating: Boolean = false,
    val notice: Int? = null,
    /** 用户当前展开详情的模型版本。 */
    val expandedModelIds: Set<String> = emptySet(),
)

/** 本地模型页的显式操作；请求移除和确认移除是两个独立事件。 */
sealed interface LocalModelAction {
    /** 展开或收起模型详细信息。 */
    data class ToggleDetails(val id: String) : LocalModelAction
    /** 开始指定内置版本的下载。 */
    data class Download(val id: String) : LocalModelAction
    /** 取消下载并清理临时文件。 */
    data class CancelDownload(val id: String) : LocalModelAction
    /** 只打开确认弹窗，不执行文件操作。 */
    data class RequestRemoval(val id: String) : LocalModelAction
    /** 确认移除当前弹窗指向的版本。 */
    data object ConfirmRemoval : LocalModelAction
    /** 取消弹窗，不执行文件操作。 */
    data object DismissRemoval : LocalModelAction
}
