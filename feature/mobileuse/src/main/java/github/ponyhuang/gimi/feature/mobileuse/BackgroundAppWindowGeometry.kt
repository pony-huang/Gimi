package github.ponyhuang.gimi.feature.mobileuse

/**
 * 固定只读小窗的浮点几何；允许横向贴边隐藏，安全区域不足时仅等比缩小预览。
 * @property left 窗口左边距，单位为宿主像素。
 * @property top 窗口顶边距，单位为宿主像素。
 * @property width 窗口宽度，单位为宿主像素。
 * @property aspectRatio 副屏画面的宽高比，窗口不包含占位工具栏。
 */
data class BackgroundAppWindowGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val aspectRatio: Float,
) {
    init {
        require(aspectRatio > 0f && aspectRatio.isFinite() && width > 0f && width.isFinite())
    }

    val height: Float get() = width / aspectRatio

    fun constrain(bounds: BackgroundAppWindowBounds, minVisibleWidth: Float = width): BackgroundAppWindowGeometry {
        val maxWidth = minOf(bounds.right - bounds.left,
            (bounds.bottom - bounds.top).coerceAtLeast(1f) * aspectRatio).coerceAtLeast(1f)
        val nextWidth = width.coerceAtMost(maxWidth)
        val nextHeight = nextWidth / aspectRatio
        val visibleWidth = minVisibleWidth.coerceIn(1f, nextWidth)
        return copy(
            width = nextWidth,
            left = left.coerceIn(bounds.left - nextWidth + visibleWidth, bounds.right - visibleWidth),
            top = top.coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - nextHeight)),
        )
    }

    fun moveBy(dx: Float, dy: Float, bounds: BackgroundAppWindowBounds, minVisibleWidth: Float = width): BackgroundAppWindowGeometry =
        copy(left = left + dx, top = top + dy).constrain(bounds, minVisibleWidth)
}

/** 避开系统栏、切口和键盘的小窗安全区域，单位为宿主像素。 */
data class BackgroundAppWindowBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
