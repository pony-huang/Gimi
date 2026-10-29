package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 工具入口和功能列表共享的图标；相邻文字提供语义，避免重复朗读。 */
@Composable
fun FunctionIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_function_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview(name = "Function · 浅色", showBackground = true)
@Composable
private fun FunctionIconLightPreview() {
    AsssistantaiTheme(darkTheme = false) { FunctionIcon(Modifier.size(24.dp)) }
}

@Preview(name = "Function · 深色", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun FunctionIconDarkPreview() {
    AsssistantaiTheme(darkTheme = true) { FunctionIcon(Modifier.size(24.dp)) }
}
