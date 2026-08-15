package github.ponyhuang.gimi.domain.appupdate.repository

import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import kotlinx.coroutines.flow.StateFlow

/** 应用更新的整体状态机。 */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class Available(
        val info: AppUpdateInfo,
        val currentVersion: String,
    ) : AppUpdateState

    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Float,
    ) : AppUpdateState

    data class Downloaded(
        val info: AppUpdateInfo,
        /** 已下载 APK 的绝对路径（缓存目录内）。 */
        val apkPath: String,
        /** 安装包签名与当前应用不一致，需先卸载。 */
        val signatureMismatch: Boolean,
    ) : AppUpdateState

    data class Failed(val failure: UpdateFailure) : AppUpdateState
}

enum class UpdateFailure { Network, RateLimited, NoCompatibleApk, ChecksumMismatch }

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Error(val failure: UpdateFailure) : UpdateCheckResult
}

interface AppUpdateRepository {
    val state: StateFlow<AppUpdateState>

    /**
     * 检查 GitHub Releases 最新版本并与当前版本比较。
     *
     * @param manual true 表示用户主动触发：总是请求网络、失败写入状态；
     * false 表示进入设置页的静默自动检查：6 小时节流（窗口内返回上次结果）、失败静默。
     */
    suspend fun checkForUpdate(manual: Boolean): UpdateCheckResult

    /** 异步开始下载，进度通过 [state] 与通知栏暴露。 */
    fun startDownload(info: AppUpdateInfo)

    fun cancelDownload()

    /** 回到 [AppUpdateState.Idle]（关闭对话框时）。 */
    fun reset()
}
