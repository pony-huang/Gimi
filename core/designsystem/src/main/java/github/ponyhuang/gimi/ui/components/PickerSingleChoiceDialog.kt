package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 单选选择对话框：大尺寸、可搜索、可分组的统一选择器外壳。
 *
 * - 大尺寸外壳（占约 88% 屏高上限），列表区域远大于旧的 440dp 紧凑弹窗，
 *   长列表（音色表、模型目录）下滚动与搜索体验明显更好；
 * - 选项行为 [GimiPickerItemRow] 圆角卡片样式：选中行尾打勾、选中行不加粗，
 *   与抽屉选择页保持同一外观；
 * - 可选 [searchField]（搜索框）与 [filterBar]（筛选 chips 等）插槽，纵向排列在列表上方；
 * - 可选 [groupTitle]：相邻同组的选项前插入分组标题（调用方需保证同组选项相邻）；
 * - 可选 [emptyContent]：自定义空态（例如「使用〈输入值〉」的行动出口），
 *   缺省为纯文案 [emptyText]；
 * - 没有 confirmButton，仅提供「取消」作为 dismiss；点选即回调 [onPick] 由调用方关闭。
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
    searchField: (@Composable () -> Unit)? = null,
    /** 搜索框下方的附加行（性别筛选 chips 等）。 */
    filterBar: (@Composable () -> Unit)? = null,
    /** 分组标题；返回 null 的选项不参与分组。同组选项必须相邻，由调用方保证。 */
    groupTitle: ((T) -> String?)? = null,
    /** 自定义空态；仅在 [options]（过滤后）为空时展示。 */
    emptyContent: (@Composable () -> Unit)? = null,
    /** 该选项是否可选；不可选的选项仍然显示，但不可点击。 */
    enabled: (T) -> Boolean = { true },
) {
    // 列表高度上限按屏高折算，而非旧的固定 440dp：小屏不至于溢出，大屏能吃到更多可视行。
    val listMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                searchField?.invoke()
                filterBar?.invoke()
                if (options.isEmpty()) {
                    emptyContent?.invoke() ?: Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.heightIn(max = listMaxHeight),
                    ) {
                        if (groupTitle == null) {
                            pickerItems(
                                options = options,
                                key = key,
                                selected = selected,
                                enabled = enabled,
                                optionTitle = optionTitle,
                                optionSubtitle = optionSubtitle,
                                onPick = onPick,
                            )
                        } else {
                            // 相邻同组合并出一个标题；数据顺序由调用方保证。
                            var lastGroup: String? = null
                            options.forEach { option ->
                                val group = groupTitle(option)
                                if (group != null && group != lastGroup) {
                                    val headerKey = "header/$group"
                                    item(key = headerKey) {
                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            // 与选项卡片内的文字对齐（卡片外边距 8dp + 行内边距 16dp）。
                                            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 2.dp),
                                        )
                                    }
                                    lastGroup = group
                                }
                                pickerItems(
                                    options = listOf(option),
                                    key = key,
                                    selected = selected,
                                    enabled = enabled,
                                    optionTitle = optionTitle,
                                    optionSubtitle = optionSubtitle,
                                    onPick = onPick,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
    )
}

/** 列表主体的单选行：统一走 [GimiPickerItemRow] 圆角卡片样式，与抽屉选择页一致。 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.pickerItems(
    options: List<T>,
    key: (T) -> Any,
    selected: (T) -> Boolean,
    enabled: (T) -> Boolean,
    optionTitle: (T) -> String,
    optionSubtitle: (T) -> String,
    onPick: (T) -> Unit,
) {
    items(options, key = key) { option ->
        val detail = optionSubtitle(option)
        GimiPickerItemRow(
            title = optionTitle(option),
            // 空白副标题按空串处理：组件不渲染空副标题行，保持单行行高。
            subtitle = if (detail.isBlank()) "" else detail,
            selected = selected(option),
            enabled = enabled(option),
            onClick = { onPick(option) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PickerSingleChoiceDialogPreview() {
    val options = listOf("选项一", "选项二", "选项三")
    GimiTheme {
        PickerSingleChoiceDialog(
            options = options,
            selected = { it == options.first() },
            key = { it },
            title = "选择模型",
            optionTitle = { it },
            optionSubtitle = { "副标题：$it" },
            emptyText = "暂无可选项",
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PickerSingleChoiceDialogEmptyPreview() {
    GimiTheme {
        PickerSingleChoiceDialog(
            options = emptyList<String>(),
            selected = { false },
            key = { it },
            title = "选择模型",
            optionTitle = { it },
            optionSubtitle = { "" },
            emptyText = "暂无可选项",
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PickerSingleChoiceDialogDisabledPreview() {
    GimiTheme {
        PickerSingleChoiceDialog(
            options = listOf("可选模型", "未接入模型"),
            selected = { it == "可选模型" },
            key = { it },
            title = "选择模型",
            optionTitle = { it },
            optionSubtitle = { if (it == "未接入模型") "暂不支持" else "已就绪" },
            emptyText = "暂无可选项",
            enabled = { it != "未接入模型" },
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "分组 · 浅色", showBackground = true)
@Preview(name = "分组 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PickerSingleChoiceDialogGroupedPreview() {
    data class Voice(val name: String, val group: String)

    val options = listOf(
        Voice("龙安灵心", "旗舰音色"),
        Voice("许南川", "旗舰音色"),
        Voice("于小云", "精品中文"),
        Voice("Emily", "精品英文"),
    )
    GimiTheme {
        PickerSingleChoiceDialog(
            options = options,
            selected = { it.name == "龙安灵心" },
            key = { it.name },
            title = "播放音色",
            optionTitle = { it.name },
            optionSubtitle = { "女 · 知心温暖" },
            emptyText = "暂无可选项",
            groupTitle = { it.group },
            onPick = {},
            onDismiss = {},
        )
    }
}
