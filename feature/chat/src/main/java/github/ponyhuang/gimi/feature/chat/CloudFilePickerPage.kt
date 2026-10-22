package github.ponyhuang.gimi.feature.chat

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileAvailability
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChoice
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.io.File

/** 同一底部抽屉内的选择页：列表单独滚动，确认按钮固定在底部。 */
@Composable
internal fun CloudFilePickerPage(
    state: CloudFilePickerState,
    onAction: (CloudFilePickerAction) -> Unit,
    onConfirm: (List<DraftAttachment>) -> Unit,
    listMaxHeight: Dp = 380.dp,
) {
    var preview by remember { mutableStateOf<CloudFile?>(null) }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.serviceName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(state.query, { onAction(CloudFilePickerAction.QueryChanged(it)) },
            label = { Text(stringResource(R.string.chat_cloud_search)) }, singleLine = true,
            trailingIcon = if (state.query.isNotEmpty()) ({ IconButton(onClick = { onAction(CloudFilePickerAction.QueryChanged("")) }) {
                Icon(Icons.Default.Close, stringResource(R.string.chat_cloud_clear_search))
            } }) else null, modifier = Modifier.fillMaxWidth())
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.hasError -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chat_cloud_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onAction(CloudFilePickerAction.Open) }) { Text(stringResource(R.string.chat_cloud_reload)) }
            }
            state.visibleChoices.isEmpty() -> Text(stringResource(if (state.choices.isEmpty()) R.string.chat_cloud_empty else R.string.chat_cloud_no_results),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
            else -> LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = listMaxHeight)
                .nestedScroll(rememberLowerBoundaryNestedScrollConnection(listState)), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.visibleChoices, key = { it.file.id }) { choice ->
                    if (choice.file.mimeType.startsWith("image/")) {
                        androidx.compose.runtime.LaunchedEffect(choice.file.id, choice.file.scope, state.localRevision) {
                            onAction(CloudFilePickerAction.LoadLocal(github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference(choice.file.id, choice.file.scope)))
                        }
                    }
                    CloudFileChoiceRow(choice, choice.file.id in state.selectedIds, state.canToggle(choice),
                        { onAction(CloudFilePickerAction.Toggle(choice.file.id)) }, { preview = choice.file })
                }
            }
        }
        Text(stringResource(R.string.chat_cloud_selection_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onConfirm(state.selectedDrafts) },
            enabled = state.selectedDrafts.isNotEmpty() && !state.isLoading && !state.hasError,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.chat_cloud_add, state.selectedDrafts.size))
        }
    }
    preview?.let { selected ->
        val file = state.choices.firstOrNull { it.file.id == selected.id && it.file.scope == selected.scope }?.file ?: selected
        ZoomableCoilImagePreviewDialog(File(file.localReference.orEmpty()), file.id, file.displayName) { preview = null }
    }
}

@Composable
private fun CloudFileChoiceRow(choice: CloudFileChoice, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    val file = choice.file
    val image = file.mimeType.startsWith("image/")
    val localReference = file.localReference
    PreferenceGroupCard {
        Row(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (image && !localReference.isNullOrBlank()) Modifier.clickable(onClick = onPreview) else Modifier),
                contentAlignment = Alignment.Center) {
                if (image && !localReference.isNullOrBlank()) {
                    UriImage(Uri.fromFile(File(localReference)), Modifier.fillMaxSize(),
                        placeholder = { Icon(Icons.Default.Image, null) }, error = { Icon(Icons.Default.Image, null) })
                } else Icon(if (image) Icons.Default.Image else Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(file.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val size = if (file.sizeBytes >= 1024 * 1024)
                    stringResource(R.string.chat_attachment_size_mb, file.sizeBytes / (1024.0 * 1024.0))
                else stringResource(R.string.chat_attachment_size_kb, file.sizeBytes / 1024.0)
                val type = file.displayName.substringAfterLast('.', file.mimeType.substringAfterLast('/')).uppercase(java.util.Locale.ROOT)
                Text(stringResource(R.string.chat_attachment_details, type, size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = if (choice.availability == CloudFileAvailability.Ready && choice.remainingMillis > 0) {
                    if (choice.remainingMillis >= 86_400_000L) stringResource(R.string.chat_cloud_expires_days, choice.remainingMillis / 86_400_000L)
                    else stringResource(R.string.chat_cloud_expires_hours, (choice.remainingMillis + 3_599_999L) / 3_600_000L)
                } else stringResource(when (choice.availability) {
                    CloudFileAvailability.Ready -> R.string.chat_cloud_ready
                    CloudFileAvailability.ReuploadRequired -> R.string.chat_cloud_reupload
                    CloudFileAvailability.SourceUnavailable -> R.string.chat_cloud_source_unavailable
                    CloudFileAvailability.ReferenceUnverified -> R.string.chat_cloud_reference_unverified
                })
                Text(status, style = MaterialTheme.typography.labelSmall,
                    color = if (choice.isSelectable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
            Checkbox(selected, null, enabled = enabled)
        }
    }
}

@Preview(name = "云文件 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "云文件 · 深色", widthDp = 390, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloudPickerPreview(@PreviewParameter(CloudFilePickerPreviewProvider::class) state: CloudFilePickerState) {
    AsssistantaiTheme { Surface { CloudFilePickerPage(state, {}, {}) } }
}

/** 真实组件保留列表、空态、搜索、加载、失败与未验证状态预览。 */
class CloudFilePickerPreviewProvider : PreviewParameterProvider<CloudFilePickerState> {
    override val values: Sequence<CloudFilePickerState>
        get() {
            val fixture = CloudFilePickerState(isOpen = true, serviceName = "OpenAI", choices = listOf(
                CloudFileChoice(CloudFile("file-1", "scope", "产品需求说明书.pdf", "application/pdf", 2516582,
                    "hash", 1, 2, null, null), CloudFileAvailability.Ready, 6 * 86_400_000L),
                CloudFileChoice(CloudFile("file-2", "scope", "历史项目资料.pdf", "application/pdf", 4500000,
                    "hash", 1, 2, null, null), CloudFileAvailability.SourceUnavailable),
            ), selectedIds = setOf("file-1"))
            return sequenceOf(fixture, fixture.copy(query = "会议纪要"),
                fixture.copy(choices = emptyList(), selectedIds = emptySet()),
                fixture.copy(choices = emptyList(), selectedIds = emptySet(), isLoading = true),
                fixture.copy(choices = emptyList(), selectedIds = emptySet(), hasError = true),
                fixture.copy(choices = fixture.choices.map { it.copy(availability = CloudFileAvailability.ReferenceUnverified) }, selectedIds = emptySet()))
        }
}
