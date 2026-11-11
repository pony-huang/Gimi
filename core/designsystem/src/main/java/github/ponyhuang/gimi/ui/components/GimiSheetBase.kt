package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 抽屉高度。
 *
 * 抽屉不会长到全屏，高度要么按窗口高度的比例给，要么给绝对值；两者都在
 * [GimiSheetBase] 内部再被上限压一次，调用方开不出全屏抽屉。
 */
@Immutable
sealed interface SheetHeight {
    /** 按窗口高度的比例。 */
    @Immutable
    data class Fraction(val value: Float) : SheetHeight

    /** 绝对高度。 */
    @Immutable
    data class Absolute(val value: Dp) : SheetHeight

    companion object {
        fun fraction(value: Float): SheetHeight = Fraction(value)
        fun dp(value: Dp): SheetHeight = Absolute(value)
    }
}

object GimiSheetDefaults {
    /** 打开时的默认高度：窗口的一半。 */
    val InitialHeight: SheetHeight = SheetHeight.Fraction(0.5f)

    /** 默认最大高度：窗口的 90%。 */
    val MaxHeight: SheetHeight = SheetHeight.Fraction(0.9f)

    /**
     * 硬上限。
     *
     * 无论调用方传什么，最大高度都不会超过窗口的这个比例——「不能全屏」由组件保证，
     * 而不是每个调用方自觉。
     */
    const val MaxHeightFraction: Float = 0.9f

    /** 顶部拖动横杆的触控高度。 */
    val DragHandleTouchHeight: Dp = 32.dp

    /**
     * 首帧为标题与底部操作区预留的高度。
     *
     * 主体的高度上限要减掉标题与底部操作区，但这两块的高度要等排版完才知道。首帧先用
     * 这个预留值把主体收住，避免抽屉先顶到全屏再缩回来；实测高度回来后就改用实测值
     * （见 [resolveChromeHeight]），预留值不会一直扣在主体上限上。
     */
    val ReservedChromeHeight: Dp = 240.dp
}

/**
 * 抽屉控制器。
 *
 * 暴露在 [LocalGimiSheetController] 上，供抽屉内的任意位置调用：典型用法是
 * 「先播完收起动画，再把选择提交出去」，避免写入发生在动画播放过程中。
 */
@OptIn(ExperimentalMaterial3Api::class)
class GimiSheetController internal constructor(
    private val sheetState: SheetState,
    private val scope: CoroutineScope,
) {
    /** 播放收起动画，动画结束后执行 [then]。 */
    fun hide(then: suspend () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            then()
        }
    }
}

/** 抽屉控制器；只在抽屉内部有值。 */
val LocalGimiSheetController: ProvidableCompositionLocal<GimiSheetController> =
    staticCompositionLocalOf { error("LocalGimiSheetController 只能在抽屉内部读取") }

/**
 * 抽屉基础容器：模态外壳 + 顶部横杆 + 连续高度 + 固定插槽骨架。
 *
 * 抽屉贴着内容长：主体的高度上限由 [initialHeight] 给出，内容比它短时抽屉就只长到
 * 内容那一截——只有三个选项时不会在下面留一大片空白。内容比上限长时（长列表）主体
 * 撑满上限并自己滚动。
 *
 * 高度是连续可拖的，没有档位：默认贴着内容长，横杆拖过之后主体固定到松手时的高度，
 * 下次打开回到 [initialHeight]。主体拖到零即视为关闭。
 *
 * 只有顶部横杆能改高度。抽屉自带的拖拽与嵌套滚动都被关掉，因此内容里的列表
 * 滚动、键盘弹出，都不会再带动抽屉变形——内容多长也撑不高抽屉。
 *
 * @param onDismissRequest 抽屉收起后回调；Material3 在收起动画播完后才回调它。
 * @param initialHeight 主体的高度上限，也是打开时的高度；内容比它短时抽屉更矮。
 * @param maxHeight 抽屉的总高度上限；仍会被 [GimiSheetDefaults.MaxHeightFraction] 压住。
 * @param height 外部指定的高度上限；抽屉分页时用它切换。拖动结果在下一次外部变更前有效。
 * @param header 标题区，固定在主体上方，不随主体滚动。
 * @param footer 底部操作区，固定在主体下方。
 * @param content 主体内容，高度由内容自身决定，滚动由调用方实现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GimiSheetBase(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialHeight: SheetHeight = GimiSheetDefaults.InitialHeight,
    maxHeight: SheetHeight = GimiSheetDefaults.MaxHeight,
    height: SheetHeight? = null,
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // 只留展开与隐藏两个状态：半展开态在这里没有意义——高度由横杆连续控制。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val controller = remember(sheetState, scope) { GimiSheetController(sheetState, scope) }
    var dismissed by remember { mutableStateOf(false) }

    fun notifyDismissed() {
        if (dismissed) return
        dismissed = true
        onDismissRequest()
    }

    ModalBottomSheet(
        // Material3 在收起动画播完后才回调它。
        onDismissRequest = ::notifyDismissed,
        sheetState = sheetState,
        // 高度由顶部横杆独家控制：关掉 Material3 自带的拖拽与嵌套滚动，
        // 内容滚动因此永远不会带动抽屉。
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = preferenceCanvasColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalGimiSheetController provides controller) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val windowHeight = this.maxHeight
                val heights = resolveSheetHeights(
                    windowHeight = windowHeight,
                    initialHeight = initialHeight,
                    maxHeight = maxHeight,
                    requestedHeight = height,
                )
                val maxSheetHeight = heights.max
                val openingHeight = heights.opening

                // 标题与底部操作区是固定高度，主体只能用剩下的那部分。
                // 首帧还没有实测值，先用预留值收住主体；测到之后立刻改用实测值，
                // 预留值不能一直参与计算，否则会把主体上限永久压低一大截。
                var headerHeight by remember { mutableStateOf<Dp?>(null) }
                var footerHeight by remember { mutableStateOf<Dp?>(null) }
                // 标题、底部操作区与横杆占的高度要先给主体留出来：主体一旦吃掉全部高度，
                // 底部操作区就会被挤成 0（它的实测高度随之归零，形成死循环）。
                val chromeHeight = resolveChromeHeight(headerHeight, footerHeight)
                val bodyLimit = resolveBodyLimit(maxSheetHeight, chromeHeight)
                // 主体的高度上限可以被横杆拖动；外部指定高度时以它为准。
                var draggedBodyHeight by remember { mutableStateOf<Dp?>(null) }
                LaunchedEffect(height, initialHeight) { draggedBodyHeight = null }
                val bodyCap = (draggedBodyHeight ?: openingHeight)
                    .coerceIn(0.dp, maxSheetHeight)
                // 拖动与排版必须用同一个上限，否则第一下拖动会先「吃掉」预留值与实测值
                // 之间的差额，看起来完全没动，内部状态却跳变一次。
                val effectiveBodyCap = bodyCap.coerceAtMost(bodyLimit)
                val density = LocalDensity.current
                var dismissStarted by remember { mutableStateOf(false) }

                fun dismiss() {
                    // 手势会连续回调，归零之后仍会收到 drag 事件，只发起一次收起动画。
                    if (dismissStarted) return
                    dismissStarted = true
                    scope.launch {
                        sheetState.hide()
                        notifyDismissed()
                    }
                }

                // 抽屉贴着内容长：高度由内容决定，最多到上限。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight),
                ) {
                    GimiSheetDragHandle(
                        onDragDelta = { deltaPx ->
                            val delta = with(density) { deltaPx.toDp() }
                            val next = effectiveBodyCap - delta
                            if (next <= 0.dp) {
                                dismiss()
                            } else {
                                draggedBodyHeight = next.coerceAtMost(bodyLimit)
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                    ) {
                        // 槽里可能给出多个子组件（标题行 + 搜索行），要竖排而不是叠在一起。
                        Column { header() }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 未拖动时贴内容高度；用户拖过之后固定到手指定下的高度，
                            // 这样短内容抽屉向下拖动也能跟手，而不是等上限缩到内容以下才有反应。
                            .then(
                                if (draggedBodyHeight != null) {
                                    Modifier.height(effectiveBodyCap)
                                } else {
                                    Modifier.heightIn(max = effectiveBodyCap)
                                }
                            )
                            .clipToBounds(),
                    ) {
                        content()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { footerHeight = with(density) { it.height.toDp() } },
                    ) {
                        // 同上：底部操作区里的多个子组件也要竖排。
                        Column { footer() }
                    }
                }
            }
        }
    }
}

/** 把 [SheetHeight] 换算成实际高度。 */
internal fun SheetHeight.resolve(windowHeight: Dp): Dp = when (this) {
    is SheetHeight.Fraction -> windowHeight * value
    is SheetHeight.Absolute -> value
}

/** 抽屉解析后的两个高度。 */
internal data class SheetHeights(
    /** 打开时的主体高度上限。 */
    val opening: Dp,
    /** 抽屉总高度上限。 */
    val max: Dp,
)

/**
 * 算出抽屉的高度。
 *
 * 上限永远不超过窗口的 [GimiSheetDefaults.MaxHeightFraction]——「不能全屏」是组件的
 * 保证，调用方即使传入更大的高度也会被压回来。
 */
internal fun resolveSheetHeights(
    windowHeight: Dp,
    initialHeight: SheetHeight,
    maxHeight: SheetHeight,
    requestedHeight: SheetHeight? = null,
): SheetHeights {
    val max = maxHeight.resolve(windowHeight)
        .coerceAtMost(windowHeight * GimiSheetDefaults.MaxHeightFraction)
    val opening = (requestedHeight ?: initialHeight).resolve(windowHeight).coerceIn(0.dp, max)
    return SheetHeights(opening = opening, max = max)
}

/**
 * 抽屉外壳占用的高度。
 *
 * 首帧还没有实测值（标题或底部操作区为 null），先用预留值把主体收住，避免抽屉先顶到
 * 全屏再缩回来；实测值一旦回来就以它为准。用 `maxOf(实测, 预留)` 会把预留值变成永久
 * 下限，长列表抽屉会被无谓地削掉一截。
 */
internal fun resolveChromeHeight(
    headerHeight: Dp?,
    footerHeight: Dp?,
    dragHandleHeight: Dp = GimiSheetDefaults.DragHandleTouchHeight,
    reservedHeight: Dp = GimiSheetDefaults.ReservedChromeHeight,
): Dp = if (headerHeight != null && footerHeight != null) {
    headerHeight + footerHeight + dragHandleHeight
} else {
    reservedHeight
}

/** 扣掉外壳后留给主体的高度，窗口再小也不会算出负值。 */
internal fun resolveBodyLimit(maxSheetHeight: Dp, chromeHeight: Dp): Dp =
    (maxSheetHeight - chromeHeight).coerceAtLeast(0.dp)

/**
 * 顶部拖动横杆。
 *
 * 触控挂在整条横杆带子上（而不是那根 4dp 的小条），手指落在哪都能拖。
 *
 * 拖拽回调通过 [rememberUpdatedState] 取最新值：`pointerInput` 的 key 是固定的，
 * 不会因为高度变化而重启，若直接捕获回调里的高度，每次手势事件都会从打开时的高度
 * 重新算，位移就累积不起来。
 */
@Composable
internal fun GimiSheetDragHandle(
    onDragDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragHandleDescription = stringResource(R.string.gimi_sheet_drag_handle)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GimiSheetDefaults.DragHandleTouchHeight)
            .semantics { contentDescription = dragHandleDescription }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDragDelta(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}
