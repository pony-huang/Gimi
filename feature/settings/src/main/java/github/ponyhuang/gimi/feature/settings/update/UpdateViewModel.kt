package github.ponyhuang.gimi.feature.settings.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateCheckResult
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateFailure
import github.ponyhuang.gimi.feature.settings.R
import java.io.File
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
    @ApplicationContext private val context: Context,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val dialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<UpdateUiState> = combine(
        appUpdateRepository.state,
        dialogVisible,
    ) { status, visible ->
        UpdateUiState(
            status = status,
            dialogVisible = visible,
            currentVersionName = currentVersionName(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UpdateUiState(currentVersionName = currentVersionName()),
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

            UpdateAction.OpenAppDetails -> emitEffect(
                UpdateEffect.LaunchIntent(appDetailsSettingsIntent()),
            )

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

    private fun confirmInstall() {
        val status = uiState.value.status
        if (status !is AppUpdateState.Downloaded) return
        val intent = if (context.packageManager.canRequestPackageInstalls()) {
            installIntent(status.apkPath)
        } else {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            )
        }
        emitEffect(UpdateEffect.LaunchIntent(intent))
    }

    private fun installIntent(apkPath: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(apkPath),
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun appDetailsSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    )

    private fun failureMessage(failure: UpdateFailure): Int = when (failure) {
        UpdateFailure.Network -> R.string.update_error_network
        UpdateFailure.RateLimited -> R.string.update_error_rate_limited
        UpdateFailure.NoCompatibleApk -> R.string.update_error_no_compatible_apk
        UpdateFailure.ChecksumMismatch -> R.string.update_error_checksum
    }

    private fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun emitEffect(effect: UpdateEffect) {
        viewModelScope.launch { mutableEffects.emit(effect) }
    }
}
