package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.LocalUserBubbleColors

/** 固定字幕视窗；仅按真实片段回执跟随，不推测逐字播放时间，也不接受手势滚动。 */
@Composable
internal fun VoiceCaptions(
    inputText: String,
    responseText: String,
    spokenText: String,
    speakingText: String,
    revision: Long,
    reconnectNotice: Boolean = false,
    toolNotice: String? = null,
    /** 场景适配：回答字幕主/次色与滚动渐隐底色；语音页传入当前主题场景色。 */
    responseColor: Color = MaterialTheme.colorScheme.onSurface,
    responseDimColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fadeColor: Color = MaterialTheme.colorScheme.background,
    /** 场景适配：提示胶囊（重连/工具执行）的容器与内容色；语音页传入当前主题场景色。 */
    noticeContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    noticeContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val heard = spokenText + speakingText
    val text = responseText.ifBlank { heard }
    // 回执文本与生成原文只保证"内容相同"，不保证逐字相同，因此按忽略空白的映射求进度。
    val spokenEnd = if (heard.isEmpty()) -1 else heardEndIn(text, spokenText)
    val heardEnd = if (heard.isEmpty()) -1 else heardEndIn(text, heard)
    val tracked = heardEnd > 0 && spokenEnd in 0..heardEnd
    val generatedOnly = heard.isBlank() && text.isNotBlank()
    val annotated = buildAnnotatedString {
        if (tracked) {
            // 三段都从生成原文切片，厂商切句与原文的差异不会把未播内容标成已播。
            withStyle(SpanStyle(color = responseDimColor)) { append(text.substring(0, spokenEnd)) }
            withStyle(SpanStyle(color = responseColor)) { append(text.substring(spokenEnd, heardEnd)) }
            withStyle(SpanStyle(color = responseDimColor)) { append(text.substring(heardEnd)) }
        } else {
            // 连空白都对不上时只展示原文，不把生成文本声称为已播放。
            withStyle(SpanStyle(color = if (generatedOnly) responseDimColor else responseColor)) { append(text) }
        }
    }
    Column(modifier) {
        if (reconnectNotice) {
            // 断线重连提示(Realtime 模式):重连即新会话,旧字幕上下文已清空。
            Surface(
                shape = MaterialTheme.shapes.large,
                color = noticeContainerColor,
                contentColor = noticeContentColor,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.voice_reconnect_notice),
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (toolNotice != null) {
            // 工具执行提示(实时助手模式):函数调用不进语音播报,执行期间的静默由该提示补位。
            Surface(
                shape = MaterialTheme.shapes.large,
                color = noticeContainerColor,
                contentColor = noticeContentColor,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.voice_tool_running, toolNotice),
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (inputText.isNotBlank()) {
            // 用户话语气泡：配色与聊天页用户气泡同源（浅色容器），靠右与回答字幕区分开。
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = MaterialTheme.shapes.large,
                    color = LocalUserBubbleColors.current.container,
                    contentColor = LocalUserBubbleColors.current.onContainer,
                ) {
                    Text(inputText, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (text.isNotBlank()) key(revision) {
            val scroll = rememberScrollState()
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val viewport = with(LocalDensity.current) { maxHeight.toPx() }
                val inset = with(LocalDensity.current) { 24.dp.toPx() }
                val focus = if (tracked) heardEnd - 1 else text.lastIndex
                LaunchedEffect(text, focus, layout, viewport, scroll.maxValue) {
                    val result = layout ?: return@LaunchedEffect
                    if (result.layoutInput.text.text != text || viewport <= 0f) return@LaunchedEffect
                    val line = result.getLineForOffset(focus.coerceIn(0, text.lastIndex))
                    val target = (result.getLineBottom(line) + inset - viewport * .68f).toInt().coerceIn(0, scroll.maxValue)
                    scroll.animateScrollTo(target, tween(450))
                }
                // enabled=false 同时禁止拖动、鼠标滚轮和用户滚动语义；文本仍保留完整可访问内容。
                Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = false).padding(vertical = 24.dp)) {
                    Text(annotated, fontSize = 29.sp, lineHeight = 43.sp, letterSpacing = (-.5).sp,
                        onTextLayout = { layout = it })
                }
                if (scroll.value > 0) Box(Modifier.fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(fadeColor, Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, fadeColor))))
            }
        }
    }
}

/**
 * 求 [heard] 在 [text] 中的结束下标，逐字比较时跳过双方空白；无法对齐返回 -1。
 *
 * 播放回执文本来自客户端切句（句首句尾空白被裁掉）或服务端字幕（自带规范化），
 * 与模型原文几乎不可能逐字相等；若按字符串前缀判断，整轮字幕都会失去进度高亮。
 */
private fun heardEndIn(text: String, heard: String): Int {
    var index = 0
    for (char in heard) {
        if (char.isWhitespace()) continue
        while (index < text.length && text[index].isWhitespace()) index++
        if (index == text.length || text[index] != char) return -1
        index++
    }
    return index
}
