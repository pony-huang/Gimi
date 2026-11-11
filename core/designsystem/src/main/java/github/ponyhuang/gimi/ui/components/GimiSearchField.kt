package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 应用统一的胶囊搜索输入框；查询与结果由页面持有，尾部支持清除或筛选操作。
 *
 * 占位文案固定单行省略：长占位一旦换行就会撑高输入框，让同一抽屉里的
 * 搜索框高度不一致。
 */
@Composable
fun GimiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    onSearch: (String) -> Unit = {},
) {
    val container = preferenceGroupCardColor()
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            // 单行省略：长占位换行会撑高输入框，让同一抽屉里的搜索框高度不一致。
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Preview(name = "搜索框 · 浅色", showBackground = true, widthDp = 390)
@Preview(name = "搜索框 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GimiSearchFieldPreview() {
    GimiTheme { GimiSearchField("", {}, placeholder = "搜索文件名") }
}

/** 长占位在窄框内单行省略，不撑高输入框。 */
@Preview(name = "搜索框 · 长占位", showBackground = true, widthDp = 390)
@Composable
private fun GimiSearchFieldLongPlaceholderPreview() {
    GimiTheme {
        GimiSearchField(
            "",
            {},
            placeholder = "搜索名称、音色 ID、特质或场景",
            trailingIcon = {},
        )
    }
}
