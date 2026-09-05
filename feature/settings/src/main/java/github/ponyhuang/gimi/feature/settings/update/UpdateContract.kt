package github.ponyhuang.gimi.feature.settings.update

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState

/** 检查更新的界面状态。status 直接透传 AppUpdateRepository 的状态机。 */
data class UpdateUiState(
    val status: AppUpdateState = AppUpdateState.Idle,
    val dialogVisible: Boolean = false,
    val currentVersionName: String = "",
)

sealed interface UpdateAction {
    /** 进入设置页：静默自动检查（仓库内节流）。 */
    data object ScreenEntered : UpdateAction

    /** 点击「检查更新」。 */
    data object CheckNow : UpdateAction

    data object StartDownload : UpdateAction

    data object CancelDownload : UpdateAction

    /** 点击「去安装」。 */
    data object ConfirmInstall : UpdateAction

    /** 签名不匹配时引导打开应用详情页卸载。 */
    data object OpenAppDetails : UpdateAction

    data object DismissDialog : UpdateAction
}

sealed interface UpdateEffect {
    /** 打开系统「允许安装未知来源应用」授权页。 */
    data object OpenUnknownSourceSettings : UpdateEffect

    /** 调起系统安装器安装已下载的 APK。[apkContentUri] 为 FileProvider content Uri。 */
    data class InstallApk(val apkContentUri: String) : UpdateEffect

    /** 打开系统应用详情页（签名不匹配时引导卸载）。 */
    data object OpenAppDetails : UpdateEffect

    data class ShowToast(@param:StringRes val messageRes: Int) : UpdateEffect
}
