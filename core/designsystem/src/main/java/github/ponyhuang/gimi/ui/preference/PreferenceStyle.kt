package github.ponyhuang.gimi.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** 外观只作用于显式选择的页面或抽屉，保持其他共享组件消费者的默认样式。 */
enum class PreferenceStyle { Original, Compact }

internal val LocalPreferenceStyle = staticCompositionLocalOf { PreferenceStyle.Original }

/** 在局部范围内统一列表、卡片与表单的外观，不携带业务状态。 */
@Composable
fun PreferenceStyleScope(
    style: PreferenceStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPreferenceStyle provides style, content = content)
}
