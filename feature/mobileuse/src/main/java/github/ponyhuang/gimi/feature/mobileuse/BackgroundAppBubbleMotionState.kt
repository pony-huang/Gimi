package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 圈圈完全可见时左上角的移动边界，坐标均为宿主空间内的像素。
 * @property left 完全可见的最左位置；只有到达此处或继续向外拖动才允许左侧收起。
 * @property right 完全可见的最右位置；不包含提前吸附区域。
 * @property top 避开顶部系统区域的最小纵坐标。
 * @property bottom 避开底部系统区域和键盘的最大纵坐标。
 * @property halfSize 圈圈半径，用于限制越界距离并始终保留一半入口。
 */
data class BackgroundAppBubbleBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val halfSize: Float,
) {
    init {
        require(right >= left && bottom >= top && halfSize > 0f)
    }

    internal fun constrain(position: Offset): Offset = Offset(
        position.x.coerceIn(left - halfSize, right + halfSize),
        position.y.coerceIn(top, bottom),
    )

    internal fun hiddenX(x: Float): Float? = when {
        x <= left -> left - halfSize
        x >= right -> right + halfSize
        else -> null
    }
}

/**
 * 系统浮窗与应用内入口共用的呈现状态：自由停留、触边延时收起和可中断的运动。
 * 宿主提供生命周期作用域及安全边界，并将 [position] 映射到实际窗口或 Compose 偏移。
 * @param scope 宿主生命周期内的作用域，必须包含用于逐帧动画的 Compose 帧时钟。
 */
@Stable
class BackgroundAppBubbleMotionState(
    private val scope: CoroutineScope,
    initialPosition: Offset,
) {
    var position by mutableStateOf(initialPosition)
        private set
    private var bounds: BackgroundAppBubbleBounds? = null
    private var dragging = false
    private var hideJob: Job? = null

    fun updateBounds(newBounds: BackgroundAppBubbleBounds) {
        if (bounds == newBounds) return
        stop()
        bounds = newBounds
        position = newBounds.constrain(position)
        if (!dragging) scheduleHide()
    }

    fun beginDrag() {
        // 保留动画已经绘制的位置，触摸不能把半隐藏的入口瞬间推回屏幕内。
        stop()
        dragging = true
    }

    fun dragBy(delta: Offset) {
        if (!dragging) return
        position = bounds?.constrain(position + delta) ?: return
    }

    fun endDrag() {
        dragging = false
        scheduleHide()
    }

    /** 宿主销毁或切换呈现方式时，取消旧入口的延时任务及动画。 */
    fun stop() {
        hideJob?.cancel()
        hideJob = null
    }

    private fun scheduleHide() {
        stop()
        val targetX = bounds?.hiddenX(position.x) ?: return
        if (targetX == position.x) return
        hideJob = scope.launch {
            delay(2000)
            // 只收起真正碰到边界的圈圈；中间位置不吸附，也不启动定时移动。
            animate(position.x, targetX, animationSpec = tween(320, easing = FastOutSlowInEasing)) { x, _ ->
                position = position.copy(x = x)
            }
        }
    }
}
