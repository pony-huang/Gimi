package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.conversation.model.MessageRole
import github.ponyhuang.asssistantai.ui.theme.LocalUserBubbleColors


/**
 * 聊天气泡包装器。
 *
 * 统一消息气泡的外观：自动根据 [role] 处理对齐方向、背景色、圆角形状
 * 以及内容前景色。内部内容（文本、图片、富文本等）只需关心自身排版，
 * 无需重复处理气泡壳样式。
 *
 * @param role      消息角色，决定左右对齐和配色
 * @param modifier  修饰符（气泡整体容器的 Modifier）
 * @param content   气泡内部内容，可以是任意 Composable
 */
@Composable
fun ChatBubble(
    role: MessageRole,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isUser = role == MessageRole.User
    val userBubbleColors = LocalUserBubbleColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    content()
                }
            }
        }
    }
}
