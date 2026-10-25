package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.ui.components.GimiListDefaults
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

/**
 * 列表区域的绝对高度上限。模型再多也只让列表内部滚动，抽屉不会一路顶到屏幕上方。
 */
private val ModelPickerListMaxHeight = 380.dp

/** 列表最多占用的可用高度比例，用于在矮窗口（横屏、分屏）下继续收缩。 */
private const val ModelPickerListHeightFraction = 0.55f

@Composable
private fun ChatModelPickerContent(
    options: List<EnabledModelRow>,
    selection: ModelSelection?,
    onPick: (EnabledModelRow) -> Unit,
) {
    // 高度上限必须来自抽屉自身拿到的窗口约束。若改用 LocalConfiguration.screenHeightDp，
    // 键盘显隐或窗口配置变化会让上限被重算，抽屉高度随之在滚动过程中反复变化而抽动。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val listMaxHeight = (maxHeight * ModelPickerListHeightFraction)
            .coerceAtMost(ModelPickerListMaxHeight)
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
                    .heightIn(max = listMaxHeight),
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
                                .heightIn(min = GimiListDefaults.MinHeight)
                                .padding(horizontal = 16.dp, vertical = GimiListDefaults.VerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LLMModelServiceIcon(serviceId = row.service.id, modifier = Modifier.size(28.dp), contentPadding = 3.dp)
                            Spacer(Modifier.width(14.dp))
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
                            Spacer(Modifier.width(14.dp))
                            RadioButton(selected = selected, onClick = null)
                        }
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

/** 候选项远超高度上限时，抽屉应停在固定高度并只滚动列表。 */
@Preview(name = "模型较多 · 浅色", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatModelPickerManyModelsLightPreview() {
    AsssistantaiTheme {
        Surface(color = chatCapsuleColor()) {
            ChatModelPickerContent(previewManyModelRows(), null, {})
        }
    }
}

@Preview(name = "模型较多 · 深色", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatModelPickerManyModelsDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        Surface(color = chatCapsuleColor()) {
            ChatModelPickerContent(previewManyModelRows(), null, {})
        }
    }
}

private fun previewModelRows(): List<EnabledModelRow> = previewModelServices().flatMap { service ->
    service.groups.flatMap { group -> group.models.map { EnabledModelRow(service, group, it) } }
}

private fun previewManyModelRows(): List<EnabledModelRow> {
    val service = previewModelServices().first()
    return (1..24).map { index ->
        val group = ModelGroup(
            id = "gpt",
            name = "GPT",
            models = listOf(Model(id = "gpt-4o-variant-$index", name = "GPT-4o variant $index")),
        )
        EnabledModelRow(service, group, group.models.first())
    }
}
