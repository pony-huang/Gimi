package github.ponyhuang.gimi.ui.chatcontent

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * 统一的 Markdown 文本渲染入口（业务无关共享组件）。
 *
 * 当前消费方：`:feature:chat`（聊天页消息气泡）与 `:feature:assistant`（语音助手面板）。
 * 所有 markdown 文本气泡 MUST 走本 Composable，禁止在其它位置直接调用 `Markdown(...)`。
 *
 * ## 路径决策
 *
 * - **channel 流式路径**：[chunkChannel] 非空时，`LaunchedEffect` 消费每个 chunk，
 *   `streamingState.append(chunk)` 追加到流式解析器，`Markdown(streamingMarkdownState = ...)`
 *   订阅其 snapshot 增量渲染（聊天页 reducer 的文本 delta 注入）。
 * - **全文桥接路径**：[chunkChannel] 为空且 [partial] 为 true 时（语音助手面板），
 *   调用方只发布"累积全文"，本组件按前缀 diff 把新增片段 append 进流式解析器，
 *   避免每个增量都整段重排。文本出现非前缀变化（整段覆盖/回退）时，流式状态无法回退，
 *   整体重建桥接并以当前全文重新播种。
 * - **静态路径**：[chunkChannel] 为空且 [partial] 为 false 时
 *   `Markdown(content = text)`，首帧立即可见（用户消息 / 历史回放 / 已完成消息）。
 *
 * 一旦本 Composable 消费过任意增量（channel 或桥接），即使增量来源随后消失，也继续用
 * streaming state 渲染，避免切回静态路径触发整段 markdown 重 parse / 重布局导致气泡闪烁。
 *
 * ## Channel 关闭 / 协程取消的契约
 *
 * `LaunchedEffect` 内的 `for (chunk in channel)` 在 channel 显式 `close()` 时正常退出
 * （内部吞掉 `ClosedReceiveChannelException`）；协程被取消时捕获 [CancellationException]
 * 后正常退出，不向 UI 抛出。
 *
 * @param text         完整文本内容（静态路径直接渲染；流式阶段由增量来源注入）
 * @param partial      是否处于流式生成阶段
 * @param chunkChannel 文本增量 channel；聊天页流式场景由调用方注入，助手面板传 null 走全文桥接
 * @param modifier     外层 modifier
 * @param fillAvailableWidth 是否占满可用宽度；用户气泡传 false 让短文本按固有宽度换行
 */
@Composable
fun ChatTextContent(
    text: String,
    partial: Boolean,
    chunkChannel: ReceiveChannel<String>?,
    modifier: Modifier = Modifier,
    fillAvailableWidth: Boolean = true,
) {
    // 桥接路径遇到非前缀覆盖时递增，重建内部流式状态（StreamingMarkdownState 只能 append）。
    var bridgeEpoch by remember { mutableIntStateOf(0) }
    key(bridgeEpoch) {
        ChatTextContentBody(
            text = text,
            partial = partial,
            chunkChannel = chunkChannel,
            modifier = modifier,
            fillAvailableWidth = fillAvailableWidth,
            onBridgeMismatch = { bridgeEpoch += 1 },
        )
    }
}

@Composable
private fun ChatTextContentBody(
    text: String,
    partial: Boolean,
    chunkChannel: ReceiveChannel<String>?,
    modifier: Modifier,
    fillAvailableWidth: Boolean,
    onBridgeMismatch: () -> Unit,
) {
    val streamingState = rememberStreamingMarkdownState()
    // 跟踪本 Composable 是否曾经消费过流式增量。
    // 一旦置 true，即使增量来源后续消失，也继续走 streaming state 路径，
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
    } else if (partial) {
        // 全文桥接：调用方每次发布"累积全文"，这里 diff 出新增片段再 append。
        var bridgedText by remember { mutableStateOf("") }
        LaunchedEffect(text, streamingState) {
            when {
                text.isEmpty() -> Unit
                text.startsWith(bridgedText) -> {
                    if (text.length > bridgedText.length) {
                        streamingState.append(text.substring(bridgedText.length))
                        streamedAnyChunk = true
                    }
                    bridgedText = text
                }
                // 非前缀覆盖（completed 事件整段替换等）：重建桥接，
                // 重组后以当前全文重新播种流式状态。
                else -> onBridgeMismatch()
            }
        }
    }

    // 渲染路径决策：
    // - 活跃流式（partial 且有 channel）：走 streaming 路径
    // - 本 Composition 内曾消费过增量（channel 或桥接）：走 streaming 路径
    // - 其它（用户消息 / 历史回放 / 滚回已完成消息）：走静态路径
    //
    // 关键边界：用户滚离再滚回已完成消息时，LazyColumn 重新 Composition，
    // 本地 `streamingState` 和 `streamedAnyChunk` 被重置；但 `chunkChannel` 引用仍在，
    // 仅用 `chunkChannel != null || streamedAnyChunk` 会用空 state 渲染导致气泡只剩壳。
    // `partial` 是消息级稳定信号，用它做 gating 能正确落到静态路径 + text。
    val useStreamingState = (partial && chunkChannel != null) || streamedAnyChunk

    val contentModifier = if (fillAvailableWidth) modifier.fillMaxWidth() else modifier
    val markdownModifier = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier

    // 代码高亮：语法主题跟随当前生效的主题（深色覆盖也生效），Atom 配色。
    // showHeader 显示代码语言 + 复制按钮，长代码块更友好。
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlightsBuilder = remember(isDarkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDarkTheme))
    }
    val components = remember(highlightsBuilder) {
        markdownComponents(
            codeBlock = {
                MarkdownHighlightedCodeBlock(
                    content = it.content,
                    node = it.node,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true,
                )
            },
            codeFence = {
                MarkdownHighlightedCodeFence(
                    content = it.content,
                    node = it.node,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true,
                )
            },
        )
    }

    SelectionContainer(modifier = contentModifier) {
        if (useStreamingState) {
            Markdown(
                streamingMarkdownState = streamingState,
                imageTransformer = Coil3ImageTransformerImpl,
                components = components,
                modifier = markdownModifier,
                typography = chatContentMarkdownTypography(),
            )
        } else {
            Markdown(
                content = text,
                imageTransformer = Coil3ImageTransformerImpl,
                components = components,
                modifier = markdownModifier,
                typography = chatContentMarkdownTypography(),
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
private fun chatContentMarkdownTypography() = markdownTypography(
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
private fun ChatTextContentPreview() {
    ChatTextContent("""
        # h1
        ## h2
        ### h3
        ```java
        var a = 123
        ```
    """.trimIndent(), false, null, modifier = Modifier.padding(8.dp))
}
