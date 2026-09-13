package github.ponyhuang.gimi.feature.workfiles

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult

/**
 * 工作文件设置页的唯一不可变状态。
 *
 * @property directories 有序工作目录列表。
 * @property directoryPickerRequestId 非空时请求 Route 打开一次目录选择器。
 * @property operationError 最近一次目录操作错误。
 */
data class WorkFilesSettingsUiState(
    val directories: List<WorkDirectory> = emptyList(),
    val directoryPickerRequestId: Int? = null,
    val operationError: WorkDirectoryOperationResult.Failure? = null,
)

sealed interface WorkFilesSettingsAction {
    /** 请求添加一个新的 SAF 工作目录。 */
    data object RequestAddDirectory : WorkFilesSettingsAction

    /** 请求用新的 SAF tree 授权替换指定目录的失效授权。 */
    data class RequestReauthorizeDirectory(val id: String) : WorkFilesSettingsAction

    /** Route 返回的目录选择结果；空值表示用户取消。 */
    data class DirectorySelected(val uri: String?) : WorkFilesSettingsAction

    /** 按稳定 ID 删除目录配置。 */
    data class RemoveDirectory(val id: String) : WorkFilesSettingsAction

    /** 按稳定 ID 切换目录是否参与搜索。 */
    data class SetDirectoryEnabled(
        val id: String,
        val enabled: Boolean,
    ) : WorkFilesSettingsAction

    /** 标记一次 picker 请求已交给系统处理。 */
    data class DirectoryPickerHandled(val requestId: Int) : WorkFilesSettingsAction

    /** 清除最近一次目录操作的页面反馈。 */
    data object ClearOperationFeedback : WorkFilesSettingsAction
}
