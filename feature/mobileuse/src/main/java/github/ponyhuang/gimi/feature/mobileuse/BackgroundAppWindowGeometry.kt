package github.ponyhuang.gimi.feature.mobileuse

/**
 * 小窗的浮点几何，保留细微拖动；尺寸只缩放预览，不修改副屏分辨率。
 * @property left 窗口左边距，单位为宿主像素。
 * @property top 窗口顶边距，单位为宿主像素。
 * @property width 窗口宽度，单位为宿主像素。
 * @property aspectRatio 副屏画面的宽高比，不包括窗口控制栏。
 * @property chromeHeight 上下控制栏的总高度，单位为宿主像素。
 */
data class BackgroundAppWindowGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val aspectRatio: Float,
    val chromeHeight: Float,
) {
    init {
        require(aspectRatio > 0f && aspectRatio.isFinite() && chromeHeight >= 0f)
    }

    val height: Float get() = width / aspectRatio + chromeHeight

    fun constrain(bounds: BackgroundAppWindowBounds): BackgroundAppWindowGeometry {
        val maxWidth = minOf(bounds.right - bounds.left,
            ((bounds.bottom - bounds.top - chromeHeight).coerceAtLeast(1f)) * aspectRatio).coerceAtLeast(1f)
        val nextWidth = width.coerceIn(minOf(bounds.minWidth, maxWidth), maxWidth)
        val nextHeight = nextWidth / aspectRatio + chromeHeight
        return copy(
            width = nextWidth,
            left = left.coerceIn(bounds.left, maxOf(bounds.left, bounds.right - nextWidth)),
            top = top.coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - nextHeight)),
        )
    }

    fun moveBy(dx: Float, dy: Float, bounds: BackgroundAppWindowBounds): BackgroundAppWindowGeometry =
        copy(left = left + dx, top = top + dy).constrain(bounds)

    fun resizeBy(dx: Float, dy: Float, bounds: BackgroundAppWindowBounds): BackgroundAppWindowGeometry {
        // 将手势投影到等比缩放方向，横向或纵向拖动都能连续调节大小。
        val deltaWidth = (dx * aspectRatio * aspectRatio + dy * aspectRatio) / (aspectRatio * aspectRatio + 1f)
        val anchoredBounds = bounds.copy(left = maxOf(left, bounds.left), top = maxOf(top, bounds.top))
        return copy(width = width + deltaWidth).constrain(anchoredBounds)
    }
}

/**
 * 避开系统栏、切口和键盘的小窗安全区域。
 * @property minWidth 正常空间下的最小宽度；空间不足时安全区域优先。
 */
data class BackgroundAppWindowBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val minWidth: Float,
)
