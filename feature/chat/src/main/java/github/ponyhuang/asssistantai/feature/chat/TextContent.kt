package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * 统一的文本渲染入口 — 所有 markdown 文本气泡（[MessageBubble] / [ThoughtBubble] /
 * [ChatBubbleText]） MUST 走本 Composable，禁止在其它位置直接调用 `Markdown(...)`。
 *
 * ## 路径决策
 *
 * - 本 Composable 持有唯一的 [StreamingMarkdownState]（由 [rememberStreamingMarkdownState] 提供）。
 * - 当 [chunkChannel] 非空时进入 streaming 路径：`LaunchedEffect` 消费每个 chunk，
 *   `streamingState.append(chunk)` 把内容追加到内部 StringBuilder 与 streaming parser，
 *   `Markdown(streamingMarkdownState = ...)` 订阅其 `snapshotStateFlow` 增量渲染。
 * - 当 [chunkChannel] 为空、且本 Composable 从未消费过 streaming chunk 时,走静态路径:
 *   `Markdown(content = text)`,首帧立即可见(用户消息 / 历史回放)。
 * - 一旦消费过任意一个 streaming chunk(`streamedAnyChunk = true`),即使后续 [chunkChannel] 变为
 *   null(例如 reducer 因外部原因导致 channel 查不到),也**继续**用 streaming state 渲染 —
 *   避免切到静态路径触发整段 markdown 重 parse / 重布局,气泡闪一下。
 *
 * ## Channel 关闭 / 协程取消的契约
 *
 * `LaunchedEffect` 内的 `for (chunk in channel)` 在以下两种情况下都 MUST 正常退出：
 * 1. Channel 被显式 `close()`：协程 for 循环读到 `ClosedReceiveChannelException` 后退出；
 *    `LaunchedEffect` 内部会吞掉该异常。
 * 2. 协程被取消（reducer `currentJob.cancel()` 或 Composable 离开 composition）：会抛
 *    [CancellationException],循环体捕获后正常退出,**不向 UI 抛出**导致 Bubble 崩溃。
 *
 * @param text         完整文本内容(用户消息 / 历史回放的首帧种子;streaming 阶段由 channel 注入)
 * @param partial      是否处于流式 partial 阶段(由调用方传入,本 Composable 不再据此切分支;
 *                     保留参数仅为 API 兼容与文档自描述)
 * @param chunkChannel reducer 暴露的文本增量 channel;streaming 时非空
 * @param modifier     外层 modifier
 * @param fillAvailableWidth Whether the content should occupy all available width. User bubbles
 *                            pass false so short text wraps its intrinsic width.
 */
@Composable
internal fun TextContent(
    text: String,
    partial: Boolean,
    chunkChannel: ReceiveChannel<String>?,
    modifier: Modifier = Modifier,
    fillAvailableWidth: Boolean = true,
) {
    val streamingState = rememberStreamingMarkdownState()
    // 跟踪本 Composable 是否曾经消费过 streaming chunk。
    // 一旦置 true,即使 chunkChannel 后续变为 null,也继续走 streaming state 路径,
    // 避免切到静态路径引发气泡闪一下。
    var streamedAnyChunk by remember { mutableStateOf(false) }

    if (chunkChannel != null) {
        LaunchedEffect(chunkChannel, streamingState) {
            try {
                for (chunk in chunkChannel) {
                    streamingState.append(chunk)
                    streamedAnyChunk = true
                }
            } catch (_: CancellationException) {
            }
        }
    }

    // 渲染路径决策:
    // - active streaming(Message 仍 partial 且有 channel):走 streaming 路径
    // - 本 Composition 内曾经消费过 chunk(防止流完成瞬间切分支引发闪烁):走 streaming 路径
    // - 其它(用户消息 / 历史回放 / **滚动后回到已完成消息**):走静态路径
    //
    // 关键边界:用户滚动离开再滚回已完成 message 时,LazyColumn 会重新 Composition,
    // 本地的 `streamingState` 和 `streamedAnyChunk` 都被重置为空/false;但 `chunkChannel`
    // 引用还在 `partChannels` map 里(直到切会话才清),仍非空。如果仅用
    // `chunkChannel != null || streamedAnyChunk`,会走到 streaming 路径用空 state 渲染,
    // 气泡只剩壳。`partial` 是 Message 级别的稳定信号(由 ViewModel 持久),用它做
    // gating 可以正确识别"已完成的 message 被滚动重排"这一场景,落到静态路径 + text。
    val useStreamingState = (partial && chunkChannel != null) || streamedAnyChunk

    val contentModifier = if (fillAvailableWidth) modifier.fillMaxWidth() else modifier
    val markdownModifier = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier
    SelectionContainer(modifier = contentModifier) {
        if (useStreamingState) {
            Markdown(
                streamingMarkdownState = streamingState,
                modifier = markdownModifier,
                typography = chatMarkdownTypography(),
            )
        } else {
            Markdown(
                content = text,
                modifier = markdownModifier,
                typography = chatMarkdownTypography(),
            )
        }
    }
}

/**
 * 聊天气泡内的 markdown 排版 —— 相比 MaterialTheme 默认值整体收敛一档，
 * 让长 markdown 在手机端更易读，不与全局 bodyLarge 冲突。
 *
 * 默认值（m3-markdown 提供）：
 *   text  = bodyLarge  (16sp)  →  14sp
 *   h1    = displayLarge(57sp)  →  22sp
 *   h2    = displayMedium(45sp) →  20sp
 *   h3    = displaySmall(36sp)  →  18sp
 *   h4    = headlineMedium(28sp)→  16sp
 *   h5    = headlineSmall(24sp) →  15sp
 *   h6    = titleLarge(22sp)    →  14sp
 *   code  = bodyMedium Monospace →  13sp
 */
@Composable
private fun chatMarkdownTypography() = markdownTypography(
    h1 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    h2 = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    h3 = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    h4 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    h5 = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    h6 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    text = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    paragraph = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    code = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace),
)

@Preview
@Composable
private fun TextContentPreview() {
    TextContent("Hello World", false, null, modifier = Modifier.padding(8.dp))
}
