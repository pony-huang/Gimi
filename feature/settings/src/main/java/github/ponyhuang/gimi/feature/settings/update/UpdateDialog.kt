package github.ponyhuang.gimi.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.markdown.m3.Markdown
import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import github.ponyhuang.gimi.domain.appupdate.model.AppVersion
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateFailure
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

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
            UpdateChangelogBody(
                currentVersionName = state.currentVersionName,
                changelog = status.info.changelog,
            )
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

/** 「发现新版本」弹窗正文：当前版本行 + 使用默认 Markdown 排版的更新说明。 */
@Composable
private fun UpdateChangelogBody(
    currentVersionName: String,
    changelog: String,
) {
    Column {
        Text(
            text = stringResource(R.string.update_subtitle_current, currentVersionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.update_changelog_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (changelog.isBlank()) {
            Text(
                text = stringResource(R.string.update_changelog_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Markdown(
                content = changelog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
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

private fun previewUpdateInfo(): AppUpdateInfo = AppUpdateInfo(
    version = AppVersion(0, 3, 0, null),
    tagName = "v0.3.0",
    title = "Gimi v0.3.0",
    changelog = """
        ## 新增功能
        - 更新说明支持 **Markdown**。
        - 支持标题、列表、链接和 `行内代码`。

        ### 问题修复
        1. 切换会话时保留草稿。
        2. 发送后清理分享附件。

        > 感谢使用 Gimi。

        [查看项目主页](https://github.com/pony-huang/Gimi)
    """.trimIndent(),
    assets = emptyList(),
    publishedAt = null,
)

@Preview(name = "更新说明 · 浅色", showBackground = true)
@Preview(name = "更新说明 · 深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpdateDialogAvailablePreview() {
    AsssistantaiTheme {
        UpdateDialog(
            state = UpdateUiState(
                status = AppUpdateState.Available(info = previewUpdateInfo(), currentVersion = "0.2.0"),
                dialogVisible = true,
                currentVersionName = "0.2.0",
            ),
            onAction = {},
        )
    }
}

@Preview(name = "更新说明 · 空状态", showBackground = true)
@Composable
private fun UpdateDialogEmptyChangelogPreview() {
    AsssistantaiTheme {
        UpdateChangelogBody("0.10.2", "")
    }
}

@Preview(name = "更新说明 · 普通文本", showBackground = true)
@Composable
private fun UpdateDialogPlainChangelogPreview() {
    AsssistantaiTheme {
        UpdateChangelogBody("0.10.2", "改善聊天体验，修复已知问题。")
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateDialogDownloadingPreview() {
    AsssistantaiTheme {
        UpdateDialog(
            state = UpdateUiState(
                status = AppUpdateState.Downloading(info = previewUpdateInfo(), progress = 0.42f),
                dialogVisible = true,
                currentVersionName = "0.2.0",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateDialogDownloadedPreview() {
    AsssistantaiTheme {
        UpdateDialog(
            state = UpdateUiState(
                status = AppUpdateState.Downloaded(
                    info = previewUpdateInfo(),
                    apkPath = "/cache/gimi-v0.3.0.apk",
                    signatureMismatch = false,
                ),
                dialogVisible = true,
                currentVersionName = "0.2.0",
            ),
            onAction = {},
        )
    }
}
