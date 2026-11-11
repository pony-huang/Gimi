package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.core.designsystem.R

/**
 * 抽屉页面里的搜索行：胶囊输入框 + 清除按钮 + 框内尾部操作位。
 *
 * 只负责一行的排版，不自带 [GimiSheetBase] 外壳，因此可以放进抽屉的
 * `AnimatedContent` 页面里，随标题行与列表一起做层级切换动画。
 * 搜索框不自动聚焦，避免页面刚出现就弹键盘吃掉半屏。
 *
 * 附加操作位（如「新增音色 ID」）放在搜索框**内部**尾部，与清除按钮并存，
 * 与选择对话框的搜索框保持同一布局；不再提供框外右侧槽位，避免两种形态分叉。
 *
 * @param query 查询词，由调用方持有。
 * @param searchPlaceholder 搜索框占位文案。
 * @param trailing 搜索框内部尾部的附加操作位；查询词非空时排在清除按钮之后。
 */
@Composable
fun GimiSheetSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    GimiSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = searchPlaceholder,
        // 框内尾部：先清除后附加操作，两者并存时顺序稳定。
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.picker_clear_search),
                        )
                    }
                }
                trailing?.invoke()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
