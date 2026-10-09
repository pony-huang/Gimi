package github.ponyhuang.gimi.feature.settings.about.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无副作用日志弹窗：有界可复制正文、加载/失败/空状态和导出入口。 */
@Composable
fun LogsDialog(
    state: LogsUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onExport: () -> Unit,
) {
    if (!state.visible) return
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.logs_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.logs_retention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    state.loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.logs_loading))
                    }
                    state.failed -> Text(stringResource(R.string.logs_read_failed))
                    state.content.isEmpty() -> Text(stringResource(R.string.logs_empty))
                    else -> {
                        if (state.truncated) {
                            Text(
                                stringResource(R.string.logs_truncated),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        SelectionContainer {
                            Text(
                                text = state.content,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (state.failed) onRetry else onExport,
                enabled = !state.loading && !state.exporting && (state.failed || state.content.isNotEmpty()),
            ) {
                Text(stringResource(when {
                    state.failed -> R.string.logs_retry
                    state.exporting -> R.string.logs_exporting
                    else -> R.string.logs_export
                }))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.logs_close)) } },
    )
}

@Preview(name = "日志-浅色", showBackground = true)
@Preview(name = "日志-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogsDialogPreview() {
    AsssistantaiTheme {
        LogsDialog(
            state = LogsUiState(visible = true, content = "10:24:01 INFO 应用启动\n10:24:12 WARN 网络中断"),
            onClose = {}, onRetry = {}, onExport = {},
        )
    }
}

@Preview(name = "日志-空", showBackground = true)
@Composable
private fun LogsEmptyPreview() {
    AsssistantaiTheme { LogsDialog(LogsUiState(visible = true), {}, {}, {}) }
}

@Preview(name = "日志-加载", showBackground = true)
@Composable
private fun LogsLoadingPreview() {
    AsssistantaiTheme { LogsDialog(LogsUiState(visible = true, loading = true), {}, {}, {}) }
}

@Preview(name = "日志-失败", showBackground = true)
@Composable
private fun LogsFailedPreview() {
    AsssistantaiTheme { LogsDialog(LogsUiState(visible = true, failed = true), {}, {}, {}) }
}

@Preview(name = "日志-截断及导出中", showBackground = true)
@Composable
private fun LogsTruncatedPreview() {
    AsssistantaiTheme {
        LogsDialog(LogsUiState(visible = true, content = "10:24:01 INFO 应用启动", truncated = true, exporting = true), {}, {}, {})
    }
}
