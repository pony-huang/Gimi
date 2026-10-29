package github.ponyhuang.gimi.ui.components

import androidx.compose.ui.unit.dp

/** 普通列表共享的最小高度、图标槽位与留白；长文字可自然撑高，缩略图与插件卡片独立布局。 */
object GimiListDefaults {
    val MinHeight = 64.dp
    val HorizontalPadding = 16.dp
    val VerticalPadding = 8.dp
    val LeadingSize = 28.dp
    val IconSize = 24.dp
    val ContentSpacing = 14.dp
    val DividerStart = HorizontalPadding + LeadingSize + ContentSpacing
}
