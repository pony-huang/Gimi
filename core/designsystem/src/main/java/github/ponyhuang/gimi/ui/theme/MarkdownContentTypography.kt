package github.ponyhuang.gimi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Markdown 字号以聊天/助手气泡为基准，其余排版继承组件的 Material 3 默认样式。
 *
 * 相对 m3-markdown 的默认值（正文 bodyLarge 16sp、h1 displayLarge 57sp、h2 displayMedium 45sp），
 * 仅收敛字号到移动端阅读档位，保留默认行高、字距、字重与引用样式。
 * 列表符号、引用、链接、表格与正文同档，避免局部元素比正文更大。
 *
 * 消费方：
 * - [github.ponyhuang.gimi.ui.chatcontent.ChatTextContent]：助手气泡 / 语音助手面板，偏移 0。
 * - `:feature:settings` 更新说明弹窗：次级阅读场景，偏移 -2。
 *
 * @param fontSizeOffsetSp 相对基准的字号偏移（sp）。0 即基准本身，-2 表示整档小 2sp。
 */
@Composable
fun markdownContentTypography(fontSizeOffsetSp: Int = 0): MarkdownTypography {
    val defaults = markdownTypography()
    return markdownTypography(
        h1 = defaults.h1.copy(fontSize = (22 + fontSizeOffsetSp).sp),
        h2 = defaults.h2.copy(fontSize = (20 + fontSizeOffsetSp).sp),
        h3 = defaults.h3.copy(fontSize = (18 + fontSizeOffsetSp).sp),
        h4 = defaults.h4.copy(fontSize = (16 + fontSizeOffsetSp).sp),
        h5 = defaults.h5.copy(fontSize = (15 + fontSizeOffsetSp).sp),
        h6 = defaults.h6.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        text = defaults.text.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        paragraph = defaults.paragraph.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        code = defaults.code.copy(fontSize = (13 + fontSizeOffsetSp).sp),
        quote = defaults.quote.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        ordered = defaults.ordered.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        bullet = defaults.bullet.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        list = defaults.list.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        table = defaults.table.copy(fontSize = (14 + fontSizeOffsetSp).sp),
        // 保留默认链接装饰与交互状态，仅让链接字号跟随正文。
        textLink = TextLinkStyles(
            style = defaults.textLink.style?.copy(fontSize = (14 + fontSizeOffsetSp).sp),
            focusedStyle = defaults.textLink.focusedStyle,
            hoveredStyle = defaults.textLink.hoveredStyle,
            pressedStyle = defaults.textLink.pressedStyle,
        ),
    )
}
