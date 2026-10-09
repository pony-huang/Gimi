package github.ponyhuang.gimi.feature.settings.about.logs

/** 日志弹窗状态；只持有有界快照，导出时由仓库重新读取完整保留日志。 */
data class LogsUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    /** 文档选择和实际写入期间均禁用重复导出。 */
    val exporting: Boolean = false,
    val content: String = "",
    val truncated: Boolean = false,
    val failed: Boolean = false,
)

/** 路由负责系统文档选择和提示，ViewModel 不依赖 Android 文档 API。 */
sealed interface LogsEffect {
    /** 打开系统另存为文档选择器。 */
    data object ChooseDestination : LogsEffect
    /** 日志导出完成。 */
    data object ExportSucceeded : LogsEffect
    /** 日志导出失败。 */
    data object ExportFailed : LogsEffect
}
