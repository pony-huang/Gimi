package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ChatMode
import kotlin.math.roundToInt

/** 分段控件整体高度与圆角；白色滑块在容器内按档位整段平移。 */
private val ChatModeSwitchHeight = 44.dp
private val ChatModeSwitchCornerRadius = 24.dp
private val ChatModeThumbCornerRadius = 20.dp

/** 每档文字的左右留白：决定自适应宽度下的呼吸感。 */
private val ChatModeSegmentHorizontalPadding = 12.dp

/**
 * 页面模式与历史筛选共用的分段控件。
 *
 * 容器宽度自适应内容：段宽取各档文字较宽者加内边距，任何语言、字号下文字都完整可读；
 * 调用方给定固定宽度（如抽屉 `fillMaxWidth`）时按给定宽度等分。
 * 选中指示不再依赖逐项背景切换，而是一枚滑块在两档之间弹性平移（档位分数 0 ↔ 1），
 * 文字颜色随选中态渐变；顶栏与抽屉共用同一实现，切档动画保持一致。
 */
@Composable
internal fun ChatModeSwitch(mode: ChatMode, onModeChange: (ChatMode) -> Unit, modifier: Modifier = Modifier) {
    // 滑块位置以档位分数表达：0 = 左档（助手），1 = 右档（Chatbot），
    // 弹簧动画让切档像系统 Switch 一样跟手滑动；实际 x 在 measure 后按段宽换算。
    val selectedIndex = ChatMode.entries.indexOf(mode).coerceIn(0, ChatMode.entries.lastIndex)
    val slide by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chatModeThumbSlide",
    )

    Layout(
        content = {
            // 滑块：无自绘内容，宽度由 measure 阶段按段宽定住后随 slide 平移。
            Box(
                Modifier
                    .clip(RoundedCornerShape(ChatModeThumbCornerRadius))
                    .background(MaterialTheme.colorScheme.primary),
            )
            ChatMode.entries.forEach { value ->
                val selected = mode == value
                val contentColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 180),
                    label = "chatModeLabel",
                )
                Text(
                    text = stringResource(if (value == ChatMode.ASSISTANT) R.string.chat_mode_assistant else R.string.chat_mode_chatbot),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(ChatModeThumbCornerRadius))
                        .selectable(selected, role = Role.Tab, onClick = { onModeChange(value) })
                        .padding(horizontal = ChatModeSegmentHorizontalPadding, vertical = 10.dp),
                )
            }
        },
        measurePolicy = { measurables, constraints ->
            val height = constraints.maxHeight
            // 先不约束宽度测各档文字，取实宽定段宽。
            val loose = Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = height)
            val labels = measurables.drop(1).map { it.measure(loose) }
            val contentWidth = labels.maxOf { it.width } * labels.size
            // 调用方给固定宽（min == max）就用给定宽；否则取内容宽，但不超出可用宽。
            val width = if (constraints.hasFixedWidth) constraints.maxWidth else minOf(contentWidth, constraints.maxWidth)
            val segment = width / labels.size
            val thumb = measurables[0].measure(Constraints.fixed(segment, height))
            layout(width, height) {
                thumb.place((slide * segment).roundToInt(), 0)
                labels.forEachIndexed { index, placeable ->
                    placeable.place(
                        index * segment + (segment - placeable.width) / 2,
                        (height - placeable.height) / 2,
                    )
                }
            }
        },
        modifier = modifier
            .height(ChatModeSwitchHeight)
            .clip(RoundedCornerShape(ChatModeSwitchCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    )
}
