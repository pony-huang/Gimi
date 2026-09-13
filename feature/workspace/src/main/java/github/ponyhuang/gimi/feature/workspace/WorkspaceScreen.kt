package github.ponyhuang.gimi.feature.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFileType
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工作区管理界面（无状态）：文件列表 + 总占用 + 多选删除。
 *
 * 状态经 [state] 进入，意图经 [onAction] 离开；打开文件预览是 UI 侧副作用，由 [onOpenFile]
 * 回调交给 Route 处理。长按文件进入多选，多选态点按切换勾选、删除需在 Route 的确认
 * 对话框中二次拍板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenFile: (WorkspaceFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isSelecting) {
                            stringResource(R.string.workspace_selected_count, state.selectedPaths.size)
                        } else {
                            stringResource(R.string.workspace_screen_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.isSelecting) {
                        IconButton(onClick = { onAction(WorkspaceAction.ExitSelection) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.workspace_exit_selection),
                            )
                        }
                    }
                },
                actions = {
                    if (state.isSelecting && state.selectedPaths.isNotEmpty() && !state.isDeleting) {
                        IconButton(onClick = { onAction(WorkspaceAction.RequestDelete) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.workspace_delete_selected),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.loadFailed -> WorkspaceLoadFailed(
                    onRetry = { onAction(WorkspaceAction.RetryLoad) },
                    modifier = Modifier.align(Alignment.Center),
                )

                state.files.isEmpty() -> WorkspaceEmpty(modifier = Modifier.fillMaxSize())

                else -> Column {
                    Text(
                        text = stringResource(
                            R.string.workspace_summary,
                            state.files.size,
                            formatWorkspaceSize(state.totalBytes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(items = state.files, key = { _, file -> file.path }) { _, file ->
                            WorkspaceRow(
                                file = file,
                                selecting = state.isSelecting,
                                selected = file.path in state.selectedPaths,
                                onClick = {
                                    if (state.isSelecting) {
                                        onAction(WorkspaceAction.ToggleSelected(file.path))
                                    } else {
                                        onOpenFile(file)
                                    }
                                },
                                onLongClick = { onAction(WorkspaceAction.ToggleSelected(file.path)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceRow(
    file: WorkspaceFile,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(file.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.workspace_file_meta,
                    formatWorkspaceSize(file.sizeBytes),
                    formatWorkspaceDate(file.lastModifiedMillis),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun WorkspaceLoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.workspace_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.workspace_retry))
        }
    }
}

@Composable
private fun WorkspaceEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.workspace_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.workspace_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun iconFor(type: WorkspaceFileType) = when (type) {
    WorkspaceFileType.IMAGE -> Icons.Default.Image
    WorkspaceFileType.AUDIO -> Icons.Default.AudioFile
    WorkspaceFileType.DOCUMENT -> Icons.Default.Description
    WorkspaceFileType.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

internal fun formatWorkspaceSize(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.1f KB".format(bytes.toDouble() / (1 shl 10))
    else -> "$bytes B"
}

private val workspaceDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatWorkspaceDate(millis: Long): String = workspaceDateFormat.format(Date(millis))

// ── Preview fixtures（沿用已确认的草稿视觉） ────────────────────────────────

private val previewFixture = listOf(
    WorkspaceFile(
        name = "微信图片_20260913-9f3c2a1b8d4e.jpg",
        path = "/data/workspace/微信图片_20260913-9f3c2a1b8d4e.jpg",
        sizeBytes = 2_516_582,
        lastModifiedMillis = 1_789_280_000_000,
        mimeType = "image/jpeg",
    ),
    WorkspaceFile(
        name = "会议纪要_20260910-3f8e12a9b4c5.pdf",
        path = "/data/workspace/会议纪要_20260910-3f8e12a9b4c5.pdf",
        sizeBytes = 190_464,
        lastModifiedMillis = 1_789_020_000_000,
        mimeType = "application/pdf",
    ),
    WorkspaceFile(
        name = "语音备忘-77a2b4c8d1e0.mp3",
        path = "/data/workspace/语音备忘-77a2b4c8d1e0.mp3",
        sizeBytes = 1_258_291,
        lastModifiedMillis = 1_788_700_000_000,
        mimeType = "audio/mpeg",
    ),
)

@Preview(name = "浏览 · 浅色", showBackground = true)
@Composable
private fun WorkspaceBrowsingLightPreview() {
    AsssistantaiTheme(darkTheme = false) {
        WorkspaceScreen(
            state = WorkspaceUiState(files = previewFixture, totalBytes = 3_965_337, isLoading = false),
            onAction = {},
            onOpenFile = {},
        )
    }
}

@Preview(name = "多选 · 深色", showBackground = true)
@Composable
private fun WorkspaceSelectingDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        WorkspaceScreen(
            state = WorkspaceUiState(
                files = previewFixture,
                totalBytes = 3_965_337,
                isLoading = false,
                selectedPaths = setOf(previewFixture[0].path, previewFixture[2].path),
            ),
            onAction = {},
            onOpenFile = {},
        )
    }
}

@Preview(name = "空态 · 浅色", showBackground = true)
@Composable
private fun WorkspaceEmptyLightPreview() {
    AsssistantaiTheme(darkTheme = false) {
        WorkspaceScreen(
            state = WorkspaceUiState(files = emptyList(), isLoading = false),
            onAction = {},
            onOpenFile = {},
        )
    }
}
