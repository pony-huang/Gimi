package github.ponyhuang.asssistantai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.core.designsystem.R

/**
 * 单选选择对话框：Material 3 单选列表样式。
 *
 * - 使用 [AlertDialog] 承载一个 [LazyColumn] 的 [ListItem] 选项；
 * - 选中行使用 `RadioButton` + 中等字重的 headline 表达选中状态；
 * - 行整体可点击（[Modifier.selectable]），背景保持透明；
 * - 没有 `confirmButton`，仅提供 `取消` 作为 dismiss。
 *
 * 该组件与具体业务类型解耦，参数全部以 lambda 形式传入，可被 Settings、Chat 等
 * 任意场景复用，避免各处的弹窗样式分叉。
 */
@Composable
fun <T> PickerSingleChoiceDialog(
    options: List<T>,
    selected: (T) -> Boolean,
    key: (T) -> Any,
    title: String,
    optionTitle: (T) -> String,
    optionSubtitle: (T) -> String,
    emptyText: String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (options.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    items(options, key = key) { option ->
                        val isSelected = selected(option)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = optionTitle(option),
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                )
                            },
                            supportingContent = {
                                val detail = optionSubtitle(option)
                                if (detail.isNotBlank()) Text(detail)
                            },
                            leadingContent = {
                                RadioButton(selected = isSelected, onClick = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onPick(option) },
                                ),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
    )
}