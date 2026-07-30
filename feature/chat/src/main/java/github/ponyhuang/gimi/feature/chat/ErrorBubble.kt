package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 错误气泡 — 用 `errorContainer` 红色色块展示 `Message.error`。
 *
 * 通过 [ChatBubble] 的 content slot 复用其对齐 / 圆角包装，但用自定义 Surface 覆盖配色。
 */
@Composable
fun ErrorBubble(
    message: Message,
    modifier: Modifier = Modifier,
) {
    ChatBubble(role = message.role, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Text(
                text = message.error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ErrorBubblePreview() {
    AsssistantaiTheme {
        ErrorBubble(message = Messages.fromError("rate limit exceeded"))
    }
}
