package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 聊天模型选择抽屉；模型与选择由宿主持有，点击行后交由宿主提交并关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatModelPickerSheet(
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    onPick: (EnabledModelRow) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = chatCapsuleColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("chat_model_picker_sheet"),
    ) {
        ChatModelPickerContent(options, selection, onPick)
    }
}

@Composable
private fun ChatModelPickerContent(
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    onPick: (EnabledModelRow) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_model_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(20.dp))
        // 候选较多时只滚动列表，标题和系统手势区域始终留在可见范围内。
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (options.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chat_model_empty_enable_first),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(options, key = { "${it.service.id}/${it.group.id}/${it.model.id}" }) { row ->
                val selected = selection?.let {
                    it.serviceId == row.service.id && it.groupId == row.group.id &&
                        it.modelId == row.model.id
                } ?: false
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else chatCapsuleColor(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(row) })
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LLMModelServiceIcon(serviceId = row.service.id, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.model.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val chatGroupCount = row.service.groups.count { group ->
                                group.models.any(Model::isChatModel)
                            }
                            Text(
                                text = if (chatGroupCount > 1) "${row.service.name} · ${row.group.name}" else row.service.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerLightPreview() {
    AsssistantaiTheme {
        Surface(color = chatCapsuleColor()) {
            ChatModelPickerContent(previewModelRows(), ModelSelection("openai", "gpt", "gpt-4o"), {})
        }
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        Surface(color = chatCapsuleColor()) {
            ChatModelPickerContent(previewModelRows(), ModelSelection("openai", "gpt", "gpt-4o"), {})
        }
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun ChatModelPickerEmptyPreview() {
    AsssistantaiTheme {
        Surface(color = chatCapsuleColor()) { ChatModelPickerContent(emptyList(), null, {}) }
    }
}

private fun previewModelRows(): List<EnabledModelRow> = previewModelServices().flatMap { service ->
    service.groups.flatMap { group -> group.models.map { EnabledModelRow(service, group, it) } }
}
