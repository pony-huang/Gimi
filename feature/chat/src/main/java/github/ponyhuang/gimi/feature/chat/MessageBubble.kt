package github.ponyhuang.gimi.feature.chat

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackStatus
import github.ponyhuang.gimi.ui.chatcontent.ChatBubbleRole
import github.ponyhuang.gimi.ui.chatcontent.ChatMessageBubble
import github.ponyhuang.gimi.ui.chatcontent.ChatTextContent
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** 把会话消息角色映射为共享气泡组件的角色（[ChatMessageBubble] 与业务模型解耦）。 */
internal fun MessageRole.toChatBubbleRole(): ChatBubbleRole = when (this) {
    MessageRole.User -> ChatBubbleRole.USER
    MessageRole.Assistant -> ChatBubbleRole.ASSISTANT
}


/**
 * 消息气泡 — 把 [Message] 渲染到 [ChatMessageBubble] 的 content slot 里。
 *
 * 这里只渲染最终回答文本、附件、错误与回答操作。thought 和工具活动统一由
 * [TurnTimelinePanel] 呈现，避免同一过程信息在正文气泡里重复出现。
 *
 * @param partChannelProvider reducer 暴露的"按 TextPart.id 取 chunk channel"函数。
 *        Composable 拿到 channel 后用 `for (chunk in channel) streamingState.append(chunk)`
 *        把 reducer 产生的文本 delta 持续送进流式解析器。
 */
@Composable
fun MessageBubble(
    message: Message,
    partChannelProvider: (partId: String) -> ReceiveChannel<String>?,
    speechPlaybackState: SpeechPlaybackState = SpeechPlaybackState(),
    onToggleSpeechPlayback: (messageId: String, text: String) -> Unit = { _, _ -> },
    onOpenDocument: (github.ponyhuang.gimi.domain.conversation.model.FileAttachment) -> Unit =
        {},
    modifier: Modifier = Modifier
) {
    val role = message.role
    val fillsBubbleWidth = role != MessageRole.User
    ChatMessageBubble(role = role.toChatBubbleRole(), modifier = modifier) {
        Column(modifier = if (fillsBubbleWidth) Modifier.fillMaxWidth() else Modifier) {
            if (message.textParts.isNotEmpty()) {
                message.textParts.filterNot { it.thought }.forEach { part ->
                    RenderTextPart(
                        part = part,
                        partial = message.partial,
                        chunkChannel = partChannelProvider(part.id),
                        fillAvailableWidth = fillsBubbleWidth,
                    )
                }
            }

            MessageAttachments(
                attachments = message.fileAttachments,
                onOpenDocument = onOpenDocument,
            )

            assistantReplyTextForCopy(message)?.let { text ->
                AssistantMessageActions(
                    messageId = message.id,
                    text = text,
                    speechPlaybackState = speechPlaybackState,
                    onToggleSpeechPlayback = onToggleSpeechPlayback,
                )
            }
        }
    }
}

/**
 * Returns the original Markdown body that may be copied from a completed assistant reply.
 * Thought content and tool activity are intentionally excluded from the user-facing reply.
 */
private fun assistantReplyTextForCopy(message: Message): String? {
    if (message.role != MessageRole.Assistant || message.partial) return null

    return message.textParts
        .asSequence()
        .filterNot { it.thought }
        .joinToString(separator = "") { it.text }
        .takeIf(String::isNotBlank)
}

/** A compact action row shown after a completed assistant reply. */
@Composable
private fun AssistantMessageActions(
    messageId: String,
    text: String,
    speechPlaybackState: SpeechPlaybackState,
    onToggleSpeechPlayback: (messageId: String, text: String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val copiedMessage = stringResource(R.string.chat_message_copied)
        // 复制成功后的短暂 ✓ 反馈：动作有始有终，不只依赖 Toast（部分 ROM 会吞掉）。
        var justCopied by remember { mutableStateOf(false) }
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("assistant response", text)),
                    )
                    justCopied = true
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    delay(1_600)
                    justCopied = false
                }
            },
        ) {
            Icon(
                imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.chat_message_copy),
                tint = if (justCopied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        val isCurrent = speechPlaybackState.messageId == messageId
        val status = if (isCurrent) speechPlaybackState.status else SpeechPlaybackStatus.Idle
        IconButton(onClick = { onToggleSpeechPlayback(messageId, text) }) {
            when (status) {
                SpeechPlaybackStatus.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                SpeechPlaybackStatus.Playing -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = stringResource(R.string.chat_message_pause_playback),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                SpeechPlaybackStatus.Paused,
                SpeechPlaybackStatus.Idle,
                -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(
                        if (status == SpeechPlaybackStatus.Paused) R.string.chat_message_resume_playback
                        else R.string.chat_message_play_reply,
                    ),
                    tint = if (status == SpeechPlaybackStatus.Paused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 流式渲染一段普通 markdown 文本。
 *
 * 薄壳 — 真正的 partial / static 双路径决策收口在 [ChatTextContent] 里。
 *
 * - `partial = true` 且 `chunkChannel != null` → 增量解析路径（`StreamingMarkdownState`）
 * - 其它 → 静态路径（`Markdown(content = part.text)`）
 *
 * 用户消息和已完成的 assistant 消息 MUST 走静态路径才能显示文字
 * （`StreamingMarkdownState` 没有"设置初始内容"的方法，只能 `append`）。
 */
@Composable
private fun RenderTextPart(
    part: TextPart,
    partial: Boolean,
    chunkChannel: ReceiveChannel<String>?,
    fillAvailableWidth: Boolean,
) {
    ChatTextContent(
        text = part.text,
        partial = partial,
        chunkChannel = chunkChannel,
        modifier = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier,
        fillAvailableWidth = fillAvailableWidth,
    )
}


@Preview(showBackground = true)
@Composable
private fun MessageBubblePreview() {
    AsssistantaiTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            MessageBubble(
                message = Messages.fromUser("帮我查一下今天上海的天气"),
                partChannelProvider = { null },
            )
            MessageBubble(
                message = Message(
                    author = "DefaultAssistant",
                    role = MessageRole.Assistant,
                    textParts = listOf(
                        TextPart(text = "上海今天晴，28°C。", thought = false),
                    ),
                ),
                partChannelProvider = { null },
            )
            MessageBubble(
                message = Message(
                    author = "DefaultAssistant",
                    role = MessageRole.Assistant,
                    textParts = listOf(
                        TextPart(text = "建议带伞，穿短袖。", thought = false),
                    ),
                    partial = true,
                ),
                partChannelProvider = { null },
            )
        }
    }
}
