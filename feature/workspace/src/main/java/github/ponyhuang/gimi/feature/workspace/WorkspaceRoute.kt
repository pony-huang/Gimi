package github.ponyhuang.gimi.feature.workspace

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import java.io.File

/**
 * 工作区管理页 Route：持有确认对话框、Toast 反馈与打开预览的 Android 侧副作用，
 * Screen 保持无状态。
 */
@Composable
fun WorkspaceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.deleteResult) {
        val result = state.deleteResult ?: return@LaunchedEffect
        val message = if (result.failedCount == 0) {
            context.getString(R.string.workspace_delete_done, result.deletedCount)
        } else {
            context.getString(
                R.string.workspace_delete_partial,
                result.deletedCount,
                result.failedCount,
            )
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(WorkspaceAction.ClearDeleteFeedback)
    }

    WorkspaceScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenFile = { openWorkspaceFile(context, it) },
        onBack = onBack,
        modifier = modifier,
    )

    if (state.pendingDeletePaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(WorkspaceAction.DismissDeleteDialog) },
            title = { Text(text = stringResource(R.string.workspace_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.workspace_delete_confirm_text,
                        state.pendingDeletePaths.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isDeleting,
                    onClick = { viewModel.onAction(WorkspaceAction.ConfirmDelete) },
                ) {
                    Text(text = stringResource(R.string.workspace_delete_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isDeleting,
                    onClick = { viewModel.onAction(WorkspaceAction.DismissDeleteDialog) },
                ) {
                    Text(text = stringResource(R.string.workspace_delete_confirm_cancel))
                }
            },
        )
    }
}

/**
 * 通过 FileProvider 打开工作区文件的系统预览。
 *
 * 复用 ChatRoute 文档预览的「拷贝到 cache 共享目录 → content URI → ACTION_VIEW」模式；
 * 每次都覆盖拷贝，避免外部查看器读到过期副本。失败时给出可读 Toast，不打断页面。
 */
private fun openWorkspaceFile(context: Context, file: WorkspaceFile) {
    runCatching {
        val previewRoot = AndroidAppDirectoryResolver(context).resolve(
            workspacePreviewDirectorySpec,
            create = true,
        )
        val safeName = file.name.replace(Regex("""[^\w.\-]"""), "_")
        val target = File(previewRoot, safeName)
        File(file.path).copyTo(target, overwrite = true)
        val uri = ShareableFileUriFactory.uriFor(context, target, workspacePreviewDirectorySpec)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, file.mimeType ?: "application/octet-stream")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.workspace_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
