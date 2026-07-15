package github.ponyhuang.asssistantai.ui.chat

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.model.FunctionCallView
import github.ponyhuang.asssistantai.model.FunctionResponseView
import github.ponyhuang.asssistantai.model.Message
import github.ponyhuang.asssistantai.model.MessageRole
import github.ponyhuang.asssistantai.model.Messages
import github.ponyhuang.asssistantai.model.TextPart
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch


/**
 * 聊天气泡文本 —— [ChatBubble] 的便捷包装，内部使用 [TextContent] 渲染，
 * 自动支持 **粗体**、*斜体*、`行内代码`、代码块、标题、列表等 Markdown 语法。
 *
 * ## 流式渲染
 *
 * `partial = true` 且 `chunkChannel != null` 时走 [TextContent] 的增量解析路径，
 * 与 [MessageBubble] / [ThoughtBubble] 的流式行为一致 —— partial 阶段每来一段增量
 * 不会触发整段重解析。
 *
 * 旧调用点 `ChatBubbleText(text, role, modifier)` 保持完全兼容（默认 `partial = false` /
 * `chunkChannel = null`，走静态路径）。
 *
 * @param text         消息文本（Markdown）
 * @param role         消息角色
 * @param chunkChannel reducer 暴露的文本增量 channel；流式场景由调用方注入
 * @param partial      是否处于流式 partial 阶段
 * @param modifier     修饰符
 */
@Composable
fun ChatBubbleText(
    text: String,
    role: MessageRole,
    chunkChannel: ReceiveChannel<String>? = null,
    partial: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ChatBubble(role = role, modifier = modifier) {
        CompositionLocalProvider {
            TextContent(
                text = text,
                partial = partial,
                chunkChannel = chunkChannel,
                modifier = if (role == MessageRole.User) modifier else modifier.fillMaxWidth(),
                fillAvailableWidth = role != MessageRole.User,
            )
        }
    }
}


/**
 * 消息气泡 — 把 [Message] 渲染到 [ChatBubble] 的 content slot 里。
 *
 * 渲染顺序：
 * 1. 工具调用 chip 行（`Message.functionCalls`）
 * 2. 工具响应 chip 行（`Message.functionResponses`）
 * 3. 每个 [TextPart]：
 *    - `thought == true` → 走 [ThoughtBubble]（同样支持流式渲染）
 *    - 否则 → 流式 Markdown（经由 [TextContent] 收口）
 *
 * @param partChannelProvider reducer 暴露的"按 TextPart.id 取 chunk channel"函数。
 *        Composable 拿到 channel 后用 `for (chunk in channel) streamingState.append(chunk)`
 *        把 reducer 产生的文本 delta 持续送进流式解析器。
 */
@Composable
fun MessageBubble(
    message: Message,
    partChannelProvider: (partId: String) -> ReceiveChannel<String>?,
    modifier: Modifier = Modifier
) {
    val role = message.role
    val fillsBubbleWidth = role != MessageRole.User
    ChatBubble(role = role, modifier = modifier) {
        Column(modifier = if (fillsBubbleWidth) Modifier.fillMaxWidth() else Modifier) {
            // 1. 工具调用 chip 行
            if (message.functionCalls.isNotEmpty()) {
                ChipRow(fillAvailableWidth = fillsBubbleWidth) {
                    message.functionCalls.forEach { call ->
                        ToolCallChip(call = call)
                    }
                }
            }

            // 2. 工具响应 chip 行
            if (message.functionResponses.isNotEmpty()) {
                ChipRow(fillAvailableWidth = fillsBubbleWidth) {
                    message.functionResponses.forEach { response ->
                        ToolResponseChip(response = response)
                    }
                }
            }

            MessageImageAttachments(images = message.imageAttachments)

            if (message.textParts.isNotEmpty() ||
                message.functionCalls.isNotEmpty() ||
                message.functionResponses.isNotEmpty()
            ) {
                message.textParts.forEach { part ->
                    RenderTextPart(
                        part = part,
                        partial = message.partial,
                        chunkChannel = partChannelProvider(part.id),
                        fillAvailableWidth = fillsBubbleWidth,
                    )
                }
            }

            assistantReplyTextForCopy(message)?.let { text ->
                AssistantMessageActions(text = text)
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
    text: String,
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
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("assistant response", text)),
                    )
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制回复",
            )
        }
    }
}

@Composable
private fun ChipRow(
    fillAvailableWidth: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = (if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * 流式渲染一段普通 markdown 文本。
 *
 * 薄壳 — 真正的 partial / static 双路径决策收口在 [TextContent] 里。
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
    TextContent(
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
                        TextPart(text = "需要先查天气才能给建议。", thought = true),
                    ),
                    partial = true,
                ),
                partChannelProvider = { null },
            )
            MessageBubble(
                message = Message(
                    author = "DefaultAssistant",
                    role = MessageRole.Assistant,
                    textParts = listOf(
                        TextPart(text = "上海今天晴，28°C。", thought = false),
                    ),
                    functionCalls = listOf(
                        FunctionCallView(
                            id = "c1",
                            name = "getCurrentWeather",
                            argsSummary = "(city=\"上海\")"
                        )
                    ),
                    functionResponses = listOf(
                        FunctionResponseView(
                            id = "c1",
                            name = "getCurrentWeather"
                        )
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
