package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 已上传文件的完整选择列表；数据由当前模型服务的注册表提供，行尾支持删除已上传文件。 */
@Composable
internal fun UploadedFilesPage(
    files: List<UploadedFileEntry>,
    onAttach: (UploadedFileEntry) -> Unit,
    onDelete: (UploadedFileEntry) -> Unit = {},
    listMaxHeight: Dp,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight)
            .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        if (files.isEmpty()) {
            item {
                GroupedListItem(first = true, last = true, withIcon = false) {
                    Text(
                        text = stringResource(R.string.chat_uploaded_files_empty),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(files, key = { _, entry -> entry.fileId }) { index, entry ->
            GroupedListItem(first = index == 0, last = index == files.lastIndex) {
                UploadedFileRow(entry, onClick = { onAttach(entry) }, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun UploadedFilesPreview() {
    GimiTheme {
        Surface {
            UploadedFilesPage(
                files = listOf(
                    UploadedFileEntry("file-1", "deepseek", "产品需求.pdf", "application/pdf", 2516582, 1794662400),
                    UploadedFileEntry("file-2", "deepseek", "接口说明.md", "text/markdown", 12288, 1794576000),
                ),
                onAttach = {},
                onDelete = {},
                listMaxHeight = 380.dp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadedFilesEmptyPreview() {
    GimiTheme { Surface { UploadedFilesPage(files = emptyList(), onAttach = {}, listMaxHeight = 380.dp) } }
}

/** 抽屉"上传的文件"行：彩色类型图标 + 名字 + 大小·创建时间副标题 + 行尾删除入口。 */
@Composable
private fun UploadedFileRow(
    entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GimiListDefaults.MinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(fileTypeIconRes(entry.mimeType)),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = uploadedFileMeta(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.chat_uploaded_file_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 行副标题：可读大小与按当前语言格式化的完整上传时间。 */
private fun uploadedFileMeta(
    entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry,
): String = buildString {
    append(
        when {
            entry.bytes >= 1024 * 1024 -> "%.1f MB".format(entry.bytes / (1024.0 * 1024.0))
            entry.bytes >= 1024 -> "${entry.bytes / 1024} KB"
            else -> "${entry.bytes} B"
        },
    )
    append(" · ")
    append(
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
            .withLocale(java.util.Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochSecond(entry.createdAtEpochSeconds)),
    )
}
