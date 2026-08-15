package github.ponyhuang.gimi.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateFailure
import github.ponyhuang.gimi.feature.settings.R

/** 检查更新对话框：发现新版本 → 下载中 → 下载完成/失败。 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onAction: (UpdateAction) -> Unit,
) {
    if (!state.dialogVisible) return
    when (val status = state.status) {
        is AppUpdateState.Available -> AvailableDialog(status, state, onAction)
        is AppUpdateState.Downloading -> DownloadingDialog(status, onAction)
        is AppUpdateState.Downloaded -> DownloadedDialog(status, onAction)
        is AppUpdateState.Failed -> FailedDialog(status, onAction)
        else -> Unit
    }
}

@Composable
private fun AvailableDialog(
    status: AppUpdateState.Available,
    state: UpdateUiState,
    onAction: (UpdateAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(UpdateAction.DismissDialog) },
        title = { Text(stringResource(R.string.update_available_title, status.info.tagName)) },
        text = {
            SelectionContainer {
                Text(
                    text = stringResource(
                        R.string.update_available_body,
                        state.currentVersionName,
                        status.info.changelog.ifBlank {
                            stringResource(R.string.update_changelog_empty)
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(UpdateAction.StartDownload) }) {
                Text(stringResource(R.string.update_download_now))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(UpdateAction.DismissDialog) }) {
                Text(stringResource(R.string.update_cancel))
            }
        },
    )
}

@Composable
private fun DownloadingDialog(
    status: AppUpdateState.Downloading,
    onAction: (UpdateAction) -> Unit,
) {
    val percent = (status.progress * 100).toInt().coerceIn(0, 100)
    AlertDialog(
        onDismissRequest = { onAction(UpdateAction.DismissDialog) },
        title = { Text(stringResource(R.string.update_downloading_title, status.info.tagName)) },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { status.progress },
                    // 关掉 M3 默认的终点指示圆点。
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(UpdateAction.CancelDownload) }) {
                Text(stringResource(R.string.update_cancel_download))
            }
        },
    )
}

@Composable
private fun DownloadedDialog(
    status: AppUpdateState.Downloaded,
    onAction: (UpdateAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(UpdateAction.DismissDialog) },
        title = { Text(stringResource(R.string.update_downloaded_title)) },
        text = {
            Text(
                if (status.signatureMismatch) {
                    stringResource(R.string.update_signature_mismatch)
                } else {
                    stringResource(R.string.update_downloaded_body, status.info.tagName)
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        if (status.signatureMismatch) {
                            UpdateAction.OpenAppDetails
                        } else {
                            UpdateAction.ConfirmInstall
                        },
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (status.signatureMismatch) {
                            R.string.update_open_app_details
                        } else {
                            R.string.update_install
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(UpdateAction.DismissDialog) }) {
                Text(stringResource(R.string.update_cancel))
            }
        },
    )
}

@Composable
private fun FailedDialog(
    status: AppUpdateState.Failed,
    onAction: (UpdateAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(UpdateAction.DismissDialog) },
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = {
            Text(
                stringResource(
                    when (status.failure) {
                        UpdateFailure.Network -> R.string.update_error_network
                        UpdateFailure.RateLimited -> R.string.update_error_rate_limited
                        UpdateFailure.NoCompatibleApk -> R.string.update_error_no_compatible_apk
                        UpdateFailure.ChecksumMismatch -> R.string.update_error_checksum
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(UpdateAction.CheckNow) }) {
                Text(stringResource(R.string.update_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(UpdateAction.DismissDialog) }) {
                Text(stringResource(R.string.update_cancel))
            }
        },
    )
}
