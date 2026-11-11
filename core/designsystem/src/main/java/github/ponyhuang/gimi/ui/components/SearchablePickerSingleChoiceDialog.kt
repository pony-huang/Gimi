package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 可搜索的紧凑单选弹窗；调用方持有查询，提供选项的可搜索文本与选择事件。 */
@Composable
fun <T> SearchablePickerSingleChoiceDialog(
    options: List<T>,
    query: String,
    onQueryChange: (String) -> Unit,
    searchText: (T) -> String,
    selected: (T) -> Boolean,
    key: (T) -> Any,
    title: String,
    searchPlaceholder: String,
    optionTitle: (T) -> String,
    optionSubtitle: (T) -> String,
    emptyText: String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    /** 该选项是否可选；不可选的选项仍然显示，但不可点击。 */
    enabled: (T) -> Boolean = { true },
) {
    val normalizedQuery = query.trim()
    val filtered = options.filter {
        normalizedQuery.isEmpty() || searchText(it).contains(
            normalizedQuery,
            ignoreCase = true
        )
    }
    PickerSingleChoiceDialog(
        options = filtered,
        selected = selected,
        key = key,
        title = title,
        optionTitle = optionTitle,
        optionSubtitle = optionSubtitle,
        emptyText = if (options.isEmpty()) emptyText else stringResource(R.string.picker_no_search_results),
        onPick = onPick,
        onDismiss = onDismiss,
        enabled = enabled,
        searchField = {
            GimiSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = searchPlaceholder,
                trailingIcon = if (query.isEmpty()) null else {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.picker_clear_search))
                        }
                    }
                },
            )
        },
    )
}

@Preview(name = "紧凑搜索 · 浅色")
@Preview(name = "紧凑搜索 · 深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchablePickerPreview() = SearchablePickerPreviewContent("")

@Preview(name = "搜索结果")
@Composable
private fun SearchablePickerResultPreview() = SearchablePickerPreviewContent("GPT")

@Preview(name = "搜索无匹配")
@Composable
private fun SearchablePickerEmptyPreview() = SearchablePickerPreviewContent("不存在")

@Preview(name = "含禁用项 · 浅色")
@Preview(name = "含禁用项 · 深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchablePickerDisabledPreview() {
    GimiTheme {
        SearchablePickerSingleChoiceDialog(
            options = listOf("GPT-5", "Claude Sonnet", "Gemini Pro"),
            query = "", onQueryChange = {}, searchText = { it },
            selected = { it == "GPT-5" }, key = { it }, title = "选择默认模型",
            searchPlaceholder = "搜索模型或服务", optionTitle = { it },
            optionSubtitle = { if (it == "Claude Sonnet") "暂不支持" else "我的模型服务" },
            emptyText = "暂无可选项", onPick = {}, onDismiss = {},
            enabled = { it != "Claude Sonnet" },
        )
    }
}

@Composable
private fun SearchablePickerPreviewContent(query: String) {
    GimiTheme {
        SearchablePickerSingleChoiceDialog(
            options = listOf("GPT-5", "Claude Sonnet", "Gemini Pro"),
            query = query, onQueryChange = {}, searchText = { it },
            selected = { it == "GPT-5" }, key = { it }, title = "选择默认模型",
            searchPlaceholder = "搜索模型或服务", optionTitle = { it },
            optionSubtitle = { if (it == "GPT-5") "OpenAI" else "我的模型服务" },
            emptyText = "暂无可选项", onPick = {}, onDismiss = {},
        )
    }
}
