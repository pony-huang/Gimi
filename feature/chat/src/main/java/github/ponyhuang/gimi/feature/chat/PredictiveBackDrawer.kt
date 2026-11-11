package github.ponyhuang.gimi.feature.chat

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ── 预测性返回进度抽屉：几何参数（设计评审确认值）────────────────
/** 抽屉打开时，聊天主内容相对全屏的缩放。 */
private const val DrawerContentOpenScale = 0.9f
/** 打开态聊天主内容的圆角（系统预测性返回的"窗口化"观感）。 */
private val DrawerContentCornerRadius = 24.dp
/** 返回手势开始前的遮罩强度。 */
private const val DrawerScrimAlpha = 0.32f
/** 抽屉面板随返回进度向屏幕左缘收缩的额外缩放（深度感）。 */
private const val DrawerSheetRecedeScale = 0.05f
/** 抽屉面板宽度。 */
private val DrawerSheetWidth = 320.dp
/** 抽屉面板右缘圆角，与 ModalDrawerSheet 默认观感一致。 */
private val DrawerSheetCornerRadius = 28.dp
private val DrawerSheetShadowElevation = 8.dp
/** 手势/程序化开合的统一收尾时长。 */
private const val DrawerSettleDurationMillis = 260

/**
 * 预测性返回进度抽屉宿主：替换 [androidx.compose.material3.ModalNavigationDrawer]。
 *
 * 交互契约（preview-first 设计评审确认）：
 * - 打开态：主内容缩放至 90% 并带 24dp 圆角浮在黑色底上，32% 遮罩压暗，抽屉面板盖在左侧。
 * - 返回手势：[PredictiveBackHandler] 把 `BackEventCompat.progress` 映射为进度——
 *   主内容从 90% 实时恢复到 100%、圆角归零、遮罩淡出，抽屉面板 1:1 跟手左滑
 *   并带 5% 收缩；手势取消（[CancellationException]）平滑回弹到打开态。
 * - 聊天内容右滑打开、拖拽面板关闭、点击遮罩关闭均保留，跟手后按位移阈值收尾。
 * - 程序化开关与手势提交统一由目标状态 [open] 驱动收尾动画，单一状态源避免竞态。
 *
 * @param open                  目标开关状态；调用方持有并在变化时收到 [onOpenChange]。
 * @param gesturesEnabled       是否响应手势（内容右滑、拖拽、遮罩点击、预测性返回）；
 *                              全屏覆盖层（如语音页）可见期间应传 `false`。
 * @param backHandlerEnabled    预测性返回处理器是否启用；比 [gesturesEnabled] 更细的控制，
 *                              用于让多选模式的返回键优先结束选择（该 BackHandler 须在
 *                              本组件之前组合，或用本参数关闭预测性返回）。
 * @param onOpenChange          目标开关状态变化回调：手势提交、遮罩点击与拖拽收尾后上报。
 * @param drawerContent         抽屉面板内容；面板几何（宽度、圆角、缩放）由本组件负责。
 * @param content               抽屉背后的主屏幕内容。
 */
@Composable
fun PredictiveBackDrawer(
    open: Boolean,
    gesturesEnabled: Boolean,
    backHandlerEnabled: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    // 唯一的进度源：0 = 全关，1 = 全开。手势中 snapTo 跟手，收尾统一 animateTo。
    val openness = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sheetWidthPx = with(density) { DrawerSheetWidth.toPx() }
    val settleSpec = tween<Float>(DrawerSettleDurationMillis, easing = FastOutSlowInEasing)

    // 目标状态驱动的收尾动画：程序化开关、手势/返回提交后都回到这里，避免多源竞态。
    LaunchedEffect(open) {
        openness.animateTo(if (open) 1f else 0f, settleSpec)
    }

    // 预测性返回：抽屉打开时把返回手势进度映射为抽屉关闭进度。
    PredictiveBackHandler(enabled = open && gesturesEnabled && backHandlerEnabled) { progress ->
        try {
            progress.collect { event ->
                openness.snapTo(1f - event.progress.coerceIn(0f, 1f))
            }
            // 手势完成：切换目标状态，由上方 LaunchedEffect 平滑收尾。
            onOpenChange(false)
        } catch (_: CancellationException) {
            // 手势取消：回弹到打开态（open 未变，目标态动画不会自动触发）。
            openness.animateTo(1f, settleSpec)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // 主内容：随进度在"打开 = 90% + 圆角"与"关闭 = 全屏"之间连续过渡。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 在内容父节点识别横向拖动，点击与子内容滚动仍沿原事件链分发。
                .pointerInput(open, gesturesEnabled) {
                    if (open || !gesturesEnabled) return@pointerInput
                    var dragToOpenSession = false
                    var cumulativeDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            dragToOpenSession = true
                            cumulativeDrag = 0f
                        },
                        onDragEnd = {
                            if (dragToOpenSession) {
                                dragToOpenSession = false
                                if (openness.value > 0.5f) onOpenChange(true)
                                else scope.launch { openness.animateTo(0f, settleSpec) }
                            }
                        },
                        onDragCancel = {
                            if (dragToOpenSession) {
                                dragToOpenSession = false
                                scope.launch { openness.animateTo(0f, settleSpec) }
                            }
                        },
                    ) { change, dragAmount ->
                        if (!dragToOpenSession) return@detectHorizontalDragGestures
                        change.consume()
                        cumulativeDrag += dragAmount
                        val target = (cumulativeDrag / sheetWidthPx).coerceIn(0f, 1f)
                        scope.launch { openness.snapTo(target) }
                    }
                }
                .graphicsLayer {
                    val scale = 1f - (1f - DrawerContentOpenScale) * openness.value
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(DrawerContentCornerRadius * openness.value)
                    clip = true
                },
        ) {
            content()
        }
        // 遮罩：跟随进度淡入淡出；点按关闭（全屏覆盖层期间手势已整体禁用）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = DrawerScrimAlpha * openness.value }
                .background(Color.Black)
                // 透明度不影响命中测试；关闭时必须移除输入节点，让聊天控件收到触摸。
                .then(
                    if (open && gesturesEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { onOpenChange(false) }
                        }
                    } else {
                        Modifier
                    },
                ),
        )
        // 抽屉面板：1:1 跟手左滑 + 轻微收缩（左缘为原点）制造退场深度。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(DrawerSheetWidth)
                .graphicsLayer {
                    val sheetScale = 1f - DrawerSheetRecedeScale * (1f - openness.value)
                    scaleX = sheetScale
                    scaleY = sheetScale
                    translationX = -sheetWidthPx * sheetScale * (1f - openness.value)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    shape = RoundedCornerShape(
                        topEnd = DrawerSheetCornerRadius,
                        bottomEnd = DrawerSheetCornerRadius,
                    )
                    clip = true
                    shadowElevation = DrawerSheetShadowElevation.toPx()
                }
                .pointerInput(open, gesturesEnabled) {
                    if (!gesturesEnabled) return@pointerInput
                    var dragCloseSession = false
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            dragCloseSession = open
                        },
                        onDragEnd = {
                            if (dragCloseSession) {
                                dragCloseSession = false
                                if (openness.value < 0.5f) onOpenChange(false)
                                else scope.launch { openness.animateTo(1f, settleSpec) }
                            }
                        },
                        onDragCancel = {
                            if (dragCloseSession) {
                                dragCloseSession = false
                                scope.launch { openness.animateTo(1f, settleSpec) }
                            }
                        },
                    ) { change, dragAmount ->
                        if (!dragCloseSession) return@detectHorizontalDragGestures
                        change.consume()
                        val target = (openness.value + dragAmount / sheetWidthPx).coerceIn(0f, 1f)
                        scope.launch { openness.snapTo(target) }
                    }
                },
        ) {
            drawerContent()
        }

    }
}
