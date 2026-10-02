package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement

/** 一块显示的窗口位置与层级，用于拒绝被较高窗口遮挡的目标。 */
internal data class MobileWindowState(val id: Int, val layer: Int, val bounds: MobileBounds)

/** 原生节点的查找路径及动作接收节点，避免长期持有过期 AccessibilityNodeInfo。 */
internal data class MobileNodeTarget(
    val element: MobileElement,
    val path: List<Int>,
    val ownElement: MobileElement,
    val clickPath: List<Int>?,
    val clickElement: MobileElement?,
)

/** 单一显示的节点快照及事件版本；consistent 为 false 表示采集期间发生过窗口变化。 */
internal data class MobileAccessibilitySnapshot(
    val status: String,
    val revision: Long = 0,
    val lastChangeMs: Long = 0,
    val windows: List<MobileWindowState> = emptyList(),
    val targets: Map<String, MobileNodeTarget> = emptyMap(),
    val truncated: Boolean = false,
    val consistent: Boolean = true,
)

/** 较高窗口覆盖目标中心时，不向底层节点投递动作。 */
internal fun List<MobileWindowState>.covers(element: MobileElement): Boolean = any {
    it.id != element.windowId && it.layer > (element.windowLayer ?: Int.MAX_VALUE) &&
        it.bounds.contains(element.bounds.centerX, element.bounds.centerY)
}
