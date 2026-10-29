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
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
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
import kotlin.time.Duration.Companion.milliseconds


/** 把会话消息角色映射为共享气泡组件的角色（[ChatMessageBubble] 与业务模型解耦）。 */
internal fun MessageRole.toChatBubbleRole(): ChatBubbleRole = when (this) {
    MessageRole.User -> ChatBubbleRole.USER
    MessageRole.Assistant -> ChatBubbleRole.ASSISTANT
}


/**
 * 消息气泡 — 把 [Message] 渲染到 [ChatMessageBubble] 的 content slot 里。
 *
 * 这里只渲染最终回答文本、附件、错误与回答操作。运行状态由输入框上方统一呈现，
 * 完成后的工具步骤由 [TurnActivityGroupPanel] 以折叠摘要展示，避免过程信息占用正文空间。
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
    modifier: Modifier = Modifier,
    a2uiContent: @Composable () -> Unit = {},
) {
    val role = message.role
    val fillsBubbleWidth = role != MessageRole.User
    val images = message.fileAttachments.filter { it.category == AttachmentCategory.IMAGE }
    val otherAttachments = message.fileAttachments.filterNot { it.category == AttachmentCategory.IMAGE }
    // 空白/零宽文本段（重试、还原或 runner 回显可能残留）不算可见文本，
    // 避免纯图片消息被撑成空气泡或图片下方多出一个小灰块。
    val visibleTextParts = message.textParts.filterNot { it.thought }.filter { it.hasVisibleContent }
    val hasBubbleBody = visibleTextParts.isNotEmpty() || otherAttachments.isNotEmpty()

    if (role == MessageRole.User) {
        if (images.isEmpty() && !hasBubbleBody) {
            // 完全无可见内容的用户消息（如历史回显的空事件）：不渲染空气泡。
            return
        }
        // 用户消息：灰底气泡只包正文，复制操作行与助手回答的操作行一样
        // 隔离在气泡下方（ChatGPT 式）；纯图片消息无正文时没有复制入口。
        Column(modifier = modifier) {
            if (images.isNotEmpty()) {
                // 用户图片一律以无边框自适应卡片排在气泡上方：
                // 单图按原宽高比放大、多图方形网格；文字/文件仍走灰底气泡。
                SentImages(images = images)
            }
            if (hasBubbleBody) {
                ChatMessageBubble(
                    role = ChatBubbleRole.USER,
                    modifier = if (images.isNotEmpty()) Modifier.padding(top = 4.dp) else Modifier,
                ) {
                    Column {
                        visibleTextParts.forEach { part ->
                            RenderTextPart(
                                part = part,
                                partial = message.partial,
                                chunkChannel = partChannelProvider(part.id),
                                fillAvailableWidth = false,
                            )
                        }
                        MessageAttachments(
                            attachments = otherAttachments,
                            onOpenDocument = onOpenDocument,
                        )
                    }
                }
                userReplyTextForCopy(visibleTextParts)?.let { text ->
                    UserCopyAction(text = text)
                }
            }
        }
        return
    }

    ChatMessageBubble(
        role = role.toChatBubbleRole(),
        modifier = modifier,
        assistantHorizontalInset = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            visibleTextParts.forEach { part ->
                RenderTextPart(
                    part = part,
                    partial = message.partial,
                    chunkChannel = partChannelProvider(part.id),
                    fillAvailableWidth = fillsBubbleWidth,
                )
            }

            MessageAttachments(
                attachments = message.fileAttachments,
                onOpenDocument = onOpenDocument,
            )

            a2uiContent()

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
 * 文本段是否含可见字符：除常规空白外，还要排除 NBSP、零宽空格/连接符、BOM 等
 * `isBlank` 不视为空白但渲染上完全不可见的字符，否则历史还原后会冒出空气泡。
 */
private val TextPart.hasVisibleContent: Boolean
    get() = text.any { !it.isWhitespace() && it.code !in InvisibleCharCodes }

private val InvisibleCharCodes = setOf(
    0x00A0, // NBSP
    0x200B, // zero-width space
    0x200C, // ZWNJ
    0x200D, // ZWJ
    0x200E, // LRM
    0x200F, // RLM
    0x2060, // word joiner
    0xFEFF, // BOM / zero-width NBSP
)

/** 用户消息可复制的正文：各可见文本段按序拼接；无可复制文本时返回 null。 */
private fun userReplyTextForCopy(visibleTextParts: List<TextPart>): String? =
    visibleTextParts.joinToString(separator = "") { it.text }.takeIf(String::isNotBlank)

/**
 * 用户气泡下方的复制操作行：隔离在灰底气泡外面、跟随用户气泡居右，
 * 按钮样式与助手回答的操作行（[AssistantMessageActions]）一致，仅少了语音播放入口。
 */
@Composable
private fun UserCopyAction(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CopyIconButton(text = text, clipLabel = "user message")
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

/**
 * 消息复制按钮：复制 [text] 到剪贴板，成功后短暂显示 ✓ 并弹 Toast。
 * 操作行是次要动作：按钮压到 32dp（图标 16dp），让整行更小巧、紧贴气泡。
 */
@Composable
private fun CopyIconButton(
    text: String,
    clipLabel: String,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.chat_message_copied)
    // 复制成功后的短暂 ✓ 反馈：动作有始有终，不只依赖 Toast（部分 ROM 会吞掉）。
    var justCopied by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(clipLabel, text)),
                )
                justCopied = true
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                delay(1_600.milliseconds)
                justCopied = false
            }
        },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.chat_message_copy),
            tint = if (justCopied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A compact action row shown after a completed assistant reply. */
@Composable
private fun AssistantMessageActions(
    messageId: String,
    text: String,
    speechPlaybackState: SpeechPlaybackState,
    onToggleSpeechPlayback: (messageId: String, text: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CopyIconButton(text = text, clipLabel = "assistant response")
        val isCurrent = speechPlaybackState.messageId == messageId
        val status = if (isCurrent) speechPlaybackState.status else SpeechPlaybackStatus.Idle
        IconButton(
            onClick = { onToggleSpeechPlayback(messageId, text) },
            modifier = Modifier.size(32.dp),
        ) {
            when (status) {
                SpeechPlaybackStatus.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                SpeechPlaybackStatus.Playing -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = stringResource(R.string.chat_message_pause_playback),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
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
                    modifier = Modifier.size(16.dp),
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
