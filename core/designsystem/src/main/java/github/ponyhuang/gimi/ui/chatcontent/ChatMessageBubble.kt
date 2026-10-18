package github.ponyhuang.gimi.ui.chatcontent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.ui.theme.LocalUserBubbleColors

/**
 * 聊天气泡包装器（业务无关共享组件）。
 *
 * 统一消息气泡的外观：自动根据 [role] 处理对齐方向、背景色、圆角形状
 * 以及内容前景色。内部内容（文本、图片、富文本等）只需关心自身排版，
 * 无需重复处理气泡壳样式。
 *
 * 当前消费方：`:feature:chat`（聊天页消息）与 `:feature:assistant`（语音助手面板），
 * 两侧借此保证消息视觉完全一致。
 *
 * @param role      消息角色，决定左右对齐和配色
 * @param modifier  修饰符（气泡整体容器的 Modifier）
 * @param content   气泡内部内容，可以是任意 Composable
 */
@Composable
fun ChatMessageBubble(
    role: ChatBubbleRole,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isUser = role == ChatBubbleRole.USER
    val userBubbleColors = LocalUserBubbleColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isUser) 12.dp else 0.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                color = userBubbleColors.container,
                contentColor = userBubbleColors.onContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    content()
                }
            }
        } else {
            // Assistant text intentionally has no filled bubble: this gives longer
            // answers the open reading surface used by the ChatGPT mobile app.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 助手正文沿用外层容器的边距，避免多层内缩压窄长文本。
                        .padding(vertical = 6.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatMessageBubbleUserPreview() {
    AsssistantaiTheme {
        ChatMessageBubble(role = ChatBubbleRole.USER) {
            Text(text = "用户消息示例")
        }
    }
}

@Preview(name = "助手长回复 · 浅色", showBackground = true, widthDp = 360)
@Composable
private fun ChatMessageBubbleAssistantPreview() {
    ChatMessageBubbleAssistantPreviewContent(darkTheme = false)
}

@Preview(name = "助手长回复 · 深色", showBackground = true, widthDp = 360)
@Composable
private fun ChatMessageBubbleAssistantDarkPreview() {
    ChatMessageBubbleAssistantPreviewContent(darkTheme = true)
}

@Composable
private fun ChatMessageBubbleAssistantPreviewContent(darkTheme: Boolean) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatMessageBubble(
                role = ChatBubbleRole.ASSISTANT,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "已经为你生成「自定义闹钟」卡片 👇\n\n" +
                        "• 闹钟时间：日期时间选择器（默认 07:30，可改成任意日期时间）\n\n" +
                        "• 标签：可填「起床 / 喝水 / 开会」等\n\n" +
                        "• 重复：周一到周日多选，不选就是一次性闹钟",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
