package github.ponyhuang.gimi.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** 设置页与抽屉默认使用统一紧凑样式；原始样式仅供显式指定的特殊容器使用。 */
enum class PreferenceStyle { Original, Compact }

internal val LocalPreferenceStyle = staticCompositionLocalOf { PreferenceStyle.Compact }

/** 在局部范围内统一列表、卡片与表单的外观，不携带业务状态。 */
@Composable
fun PreferenceStyleScope(
    style: PreferenceStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPreferenceStyle provides style, content = content)
}
