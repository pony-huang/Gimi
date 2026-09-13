package github.ponyhuang.gimi.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFileType
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工作区管理界面（无状态）：页头 + 文件列表 + 多选删除。
 *
 * 状态经 [state] 进入，意图经 [onAction] 离开；打开文件预览是 UI 侧副作用，由 [onOpenFile]
 * 回调交给 Route 处理。长按文件进入多选，多选态点按切换勾选、删除需在 Route 的确认
 * 对话框中二次拍板。
 *
 * 视觉沿用设置页设计系统（浅灰画布 + 大圆角分组卡片），与「工作文件」等页面保持同一语言。
 */
@Composable
internal fun WorkspaceScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenFile: (WorkspaceFile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelecting = state.isSelecting

    // 多选态左侧图标改为关闭：此处的返回语义是退出多选，而不是离开页面。
    val exitSelectionIcon: @Composable () -> Unit = {
        IconButton(onClick = { onAction(WorkspaceAction.ExitSelection) }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.workspace_exit_selection),
            )
        }
    }

    PreferenceScaffold(
        title = if (isSelecting) {
            stringResource(R.string.workspace_selected_count, state.selectedPaths.size)
        } else {
            stringResource(R.string.workspace_screen_title)
        },
        onBack = if (isSelecting) ({ onAction(WorkspaceAction.ExitSelection) }) else onBack,
        navigationIcon = if (isSelecting) exitSelectionIcon else null,
        actions = {
            if (isSelecting && state.selectedPaths.isNotEmpty() && !state.isDeleting) {
                IconButton(onClick = { onAction(WorkspaceAction.RequestDelete) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.workspace_delete_selected),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) { scaffoldModifier ->
        when {
            state.isLoading -> WorkspaceLoading(modifier = scaffoldModifier)

            state.loadFailed -> WorkspaceLoadFailed(
                onRetry = { onAction(WorkspaceAction.RetryLoad) },
                modifier = scaffoldModifier,
            )

            state.files.isEmpty() -> WorkspaceEmpty(modifier = scaffoldModifier)

            else -> WorkspaceFileList(
                state = state,
                onAction = onAction,
                onOpenFile = onOpenFile,
                modifier = scaffoldModifier,
            )
        }
    }
}

@Composable
private fun WorkspaceFileList(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenFile: (WorkspaceFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                // 占用汇总沿用分组标题的语义，与「应用存储」等标题处于同一视觉层级。
                PreferenceSectionTitle(
                    text = stringResource(
                        R.string.workspace_summary,
                        state.files.size,
                        formatWorkspaceSize(state.totalBytes),
                    ),
                )
            }
            itemsIndexed(items = state.files, key = { _, file -> file.path }) { index, file ->
                val lastIndex = state.files.lastIndex
                PreferenceListItem(
                    icon = iconFor(file.type),
                    title = file.name,
                    subtitle = stringResource(
                        R.string.workspace_file_meta,
                        formatWorkspaceSize(file.sizeBytes),
                        formatWorkspaceDate(file.lastModifiedMillis),
                    ),
                    onClick = {
                        if (state.isSelecting) {
                            onAction(WorkspaceAction.ToggleSelected(file.path))
                        } else {
                            onOpenFile(file)
                        }
                    },
                    onLongClick = { onAction(WorkspaceAction.ToggleSelected(file.path)) },
                    // 逐行拼出分组卡片的底色与圆角：既复现整卡外观，又让 LazyColumn 保持按需组合。
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(workspaceGroupShape(index = index, lastIndex = lastIndex))
                        .background(preferenceGroupCardColor()),
                    showDivider = index != lastIndex,
                    trailingContent = if (state.isSelecting) {
                        {
                            Checkbox(
                                checked = file.path in state.selectedPaths,
                                onCheckedChange = {
                                    onAction(WorkspaceAction.ToggleSelected(file.path))
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(preferenceCanvasColor()),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WorkspaceLoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(preferenceCanvasColor()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreferenceBanner(
                text = stringResource(R.string.workspace_load_failed),
                tone = PreferenceBannerTone.Error,
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.workspace_retry))
            }
        }
    }
}

@Composable
private fun WorkspaceEmpty(modifier: Modifier = Modifier) {
    PreferencePageContainer(modifier = modifier) {
        PreferenceGroupCard(modifier = Modifier.padding(top = 48.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 分组卡片圆角；与 `PreferenceGroupCard` 保持一致。 */
private val workspaceGroupCardCorner = 24.dp

/**
 * 按位置拼出分组卡片的圆角：首项圆上角、末项圆下角、中间保持直角。
 *
 * 整卡容器无法放进 LazyColumn 的单个 item（会让全部文件行一次性组合），
 * 因此按行拼出同样的外观，滚动性能与懒加载不受影响。
 */
private fun workspaceGroupShape(index: Int, lastIndex: Int): Shape = when {
    index == 0 && lastIndex == 0 -> RoundedCornerShape(workspaceGroupCardCorner)
    index == 0 -> RoundedCornerShape(
        topStart = workspaceGroupCardCorner,
        topEnd = workspaceGroupCardCorner,
    )
    index == lastIndex -> RoundedCornerShape(
        bottomStart = workspaceGroupCardCorner,
        bottomEnd = workspaceGroupCardCorner,
    )
    else -> RectangleShape
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

// ── Preview fixtures ──────────────────────────────────────────────────────

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
            onBack = {},
        )
    }
}

@Preview(name = "浏览 · 深色", showBackground = true)
@Composable
private fun WorkspaceBrowsingDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        WorkspaceScreen(
            state = WorkspaceUiState(files = previewFixture, totalBytes = 3_965_337, isLoading = false),
            onAction = {},
            onOpenFile = {},
            onBack = {},
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
            onBack = {},
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
            onBack = {},
        )
    }
}

@Preview(name = "加载失败 · 浅色", showBackground = true)
@Composable
private fun WorkspaceLoadFailedLightPreview() {
    AsssistantaiTheme(darkTheme = false) {
        WorkspaceScreen(
            state = WorkspaceUiState(isLoading = false, loadFailed = true),
            onAction = {},
            onOpenFile = {},
            onBack = {},
        )
    }
}
