package github.ponyhuang.gimi.feature.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.appupdate.repository.AppInstallEnvironment
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateCheckResult
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateFailure
import github.ponyhuang.gimi.feature.settings.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val installEnvironment: AppInstallEnvironment,
) : ViewModel() {

    private val dialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<UpdateUiState> = combine(
        appUpdateRepository.state,
        dialogVisible,
    ) { status, visible ->
        UpdateUiState(
            status = status,
            dialogVisible = visible,
            currentVersionName = installEnvironment.currentVersionName().orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UpdateUiState(currentVersionName = installEnvironment.currentVersionName().orEmpty()),
    )

    private val mutableEffects = MutableSharedFlow<UpdateEffect>()
    val effects: SharedFlow<UpdateEffect> = mutableEffects

    fun onAction(action: UpdateAction) {
        when (action) {
            UpdateAction.ScreenEntered ->
                viewModelScope.launch { appUpdateRepository.checkForUpdate(manual = false) }

            UpdateAction.CheckNow -> checkManually()

            UpdateAction.StartDownload -> {
                val status = uiState.value.status
                if (status is AppUpdateState.Available) {
                    appUpdateRepository.startDownload(status.info)
                }
            }

            UpdateAction.CancelDownload -> appUpdateRepository.cancelDownload()

            UpdateAction.ConfirmInstall -> confirmInstall()

            UpdateAction.OpenAppDetails -> emitEffect(UpdateEffect.OpenAppDetails)

            UpdateAction.DismissDialog -> {
                dialogVisible.value = false
                appUpdateRepository.reset()
            }
        }
    }

    private fun checkManually() {
        when (uiState.value.status) {
            // 已有结果或正在下载：直接重新弹出对话框，不重复请求。
            is AppUpdateState.Available,
            is AppUpdateState.Downloading,
            is AppUpdateState.Downloaded,
            -> {
                dialogVisible.value = true
                return
            }

            else -> Unit
        }
        viewModelScope.launch {
            when (val result = appUpdateRepository.checkForUpdate(manual = true)) {
                is UpdateCheckResult.UpdateAvailable -> dialogVisible.value = true
                UpdateCheckResult.UpToDate ->
                    emitEffect(UpdateEffect.ShowToast(R.string.update_up_to_date))

                is UpdateCheckResult.Error ->
                    emitEffect(UpdateEffect.ShowToast(failureMessage(result.failure)))
            }
        }
    }

    /**
     * 安装决策：已有未知来源权限 → 直接调起安装器；
     * 否则先引导用户到系统授权页（由 Route 把效果映射为具体 Intent）。
     */
    private fun confirmInstall() {
        val status = uiState.value.status
        if (status !is AppUpdateState.Downloaded) return
        if (!installEnvironment.canRequestPackageInstalls()) {
            emitEffect(UpdateEffect.OpenUnknownSourceSettings)
            return
        }
        val uri = installEnvironment.apkContentUri(status.apkPath)
        if (uri == null) {
            emitEffect(UpdateEffect.ShowToast(R.string.update_error_checksum))
            return
        }
        emitEffect(UpdateEffect.InstallApk(uri))
    }

    private fun failureMessage(failure: UpdateFailure): Int = when (failure) {
        UpdateFailure.Network -> R.string.update_error_network
        UpdateFailure.RateLimited -> R.string.update_error_rate_limited
        UpdateFailure.NoCompatibleApk -> R.string.update_error_no_compatible_apk
        UpdateFailure.ChecksumMismatch -> R.string.update_error_checksum
    }

    private fun emitEffect(effect: UpdateEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
