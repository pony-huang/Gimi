package github.ponyhuang.gimi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Markdown 正文排版 token —— 以聊天/助手气泡的档位为基准，整体收敛 [fontSizeOffsetSp]。
 *
 * 相对 m3-markdown 的默认值（正文 bodyLarge 16sp、h1 displayLarge 57sp、h2 displayMedium 45sp），
 * 这里把每一档都压到移动端阅读档位，并且行高随字号同幅平移，保持基准的字号/行高比例。
 * 列表符号、引用、链接、表格与正文同档，避免局部元素比正文更大。
 *
 * 消费方：
 * - [github.ponyhuang.gimi.ui.chatcontent.ChatTextContent]：助手气泡 / 语音助手面板，偏移 0。
 * - `:feature:settings` 更新说明弹窗：次级阅读场景，偏移 -2。
 *
 * @param fontSizeOffsetSp 相对基准的字号与行高偏移（sp）。0 即基准本身，-2 表示整档小 2sp。
 */
@Composable
fun markdownContentTypography(fontSizeOffsetSp: Int = 0): MarkdownTypography = markdownTypography(
    h1 = markdownTextStyle(22, 28, fontSizeOffsetSp, fontWeight = FontWeight.Bold),
    h2 = markdownTextStyle(20, 26, fontSizeOffsetSp, fontWeight = FontWeight.Bold),
    h3 = markdownTextStyle(18, 24, fontSizeOffsetSp, fontWeight = FontWeight.SemiBold),
    h4 = markdownTextStyle(16, 22, fontSizeOffsetSp, fontWeight = FontWeight.SemiBold),
    h5 = markdownTextStyle(15, 22, fontSizeOffsetSp, fontWeight = FontWeight.SemiBold),
    h6 = markdownTextStyle(14, 20, fontSizeOffsetSp, fontWeight = FontWeight.SemiBold),
    text = markdownTextStyle(14, 20, fontSizeOffsetSp),
    paragraph = markdownTextStyle(14, 20, fontSizeOffsetSp),
    code = markdownTextStyle(13, 20, fontSizeOffsetSp, fontFamily = FontFamily.Monospace),
    quote = markdownTextStyle(14, 20, fontSizeOffsetSp, fontStyle = FontStyle.Italic),
    ordered = markdownTextStyle(14, 20, fontSizeOffsetSp),
    bullet = markdownTextStyle(14, 20, fontSizeOffsetSp),
    list = markdownTextStyle(14, 20, fontSizeOffsetSp),
    table = markdownTextStyle(14, 20, fontSizeOffsetSp),
    // 链接跟随正文档位：默认值取 bodyLarge（16sp）+ 加粗下划线，会比正文还大。
    textLink = TextLinkStyles(
        style = markdownTextStyle(
            14,
            20,
            fontSizeOffsetSp,
            fontWeight = FontWeight.Bold,
        ).copy(textDecoration = TextDecoration.Underline).toSpanStyle(),
    ),
)

/** markdown 单档样式：字号与行高同幅平移 [offsetSp]，未指定的字形维度保持继承（null）。 */
private fun markdownTextStyle(
    fontSizeSp: Int,
    lineHeightSp: Int,
    offsetSp: Int,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily? = null,
    fontStyle: FontStyle? = null,
): TextStyle = TextStyle(
    fontSize = (fontSizeSp + offsetSp).sp,
    lineHeight = (lineHeightSp + offsetSp).sp,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    fontStyle = fontStyle,
)
