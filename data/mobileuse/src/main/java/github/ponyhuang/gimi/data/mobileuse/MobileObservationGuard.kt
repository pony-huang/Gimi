package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement

/** 最近一次可操作的观察快照；仅限一个 owner、显示和一次动作，不跨任务保存。 */
internal data class MobileObservedScreen(
    val id: String,
    val owner: String,
    val displayId: Int,
    val observedAtMs: Long,
    val frame: CapturedMobileFrame,
    val native: MobileAccessibilitySnapshot,
    val elements: List<MobileElement>,
    val pixelActionsAllowed: Boolean = true,
    val nativeActionsAllowed: Boolean = true,
    val swipeActionsAllowed: Boolean = pixelActionsAllowed,
)

/** 将观察与后续动作绑定；动作一旦尝试就消费快照，观察失败不会重新开放同一次操作。 */
internal class MobileObservationGuard {
    @Volatile var latest: MobileObservedScreen? = null
        private set
    @Volatile var requiredFrameAfterMs: Long? = null
        private set

    fun record(screen: MobileObservedScreen) { latest = screen }
    fun clear() { latest = null }
    fun reset() { latest = null; requiredFrameAfterMs = null }
    fun awaitFrameAfter(atMs: Long?) { requiredFrameAfterMs = atMs }
    fun confirmFrame(frame: CapturedMobileFrame) {
        requiredFrameAfterMs?.let { if (frame.capturedAtMs > it) requiredFrameAfterMs = null }
    }

    fun rejection(
        owner: String, displayId: Int, observationId: String, nowMs: Long,
        currentFrame: CapturedMobileFrame?, currentNative: MobileAccessibilitySnapshot,
        nativeElementId: String? = null, navigation: Boolean = false,
        coordinateBounds: MobileBounds? = null, swipe: Boolean = false,
    ): String? {
        val previous = latest ?: return "no_actionable_observation"
        if (previous.id != observationId || previous.owner != owner || previous.displayId != displayId) {
            return "observation_mismatch"
        }
        if (nowMs - previous.observedAtMs !in 0..60_000) return "observation_expired"
        if (currentFrame == null || previous.frame.width != currentFrame.width ||
            previous.frame.height != currentFrame.height || previous.frame.generation != currentFrame.generation
        ) return "frame_changed"
        if (!navigation && requiredFrameAfterMs?.let { currentFrame.capturedAtMs <= it } == true) return "no_post_action_frame"
        if (nativeElementId != null && !previous.nativeActionsAllowed) return "unstable_native_target"
        if (!navigation && nativeElementId == null &&
            !(if (swipe) previous.swipeActionsAllowed else previous.pixelActionsAllowed)
        ) return "unstable_coordinate_target"
        if (!currentNative.consistent || previous.native.status != currentNative.status ||
            previous.native.windows != currentNative.windows
        ) return "nodes_changed"
        // 返回不选择具体内容；滑动只复核起点区域的控件结构，不等待动画或计时文字停止。
        if (navigation) return null
        if (swipe) {
            if (coordinateBounds == null) return "unstable_coordinate_target"
            if (localTargets(previous.native, coordinateBounds, currentFrame, swipe = true) !=
                localTargets(currentNative, coordinateBounds, currentFrame, swipe = true)
            ) return "nodes_changed"
            return null
        }
        if (nativeElementId != null) {
            val target = previous.native.targets[nativeElementId] ?: return "unknown_native_target"
            // 只核对标签及实际接收动作的节点，其他节点刷新不使该目标失效。
            if (target != currentNative.targets[nativeElementId]) return "nodes_changed"
        } else {
            if (coordinateBounds == null) {
                if (!previous.frame.jpeg.contentEquals(currentFrame.jpeg)) return "frame_changed"
                if (previous.native.targets != currentNative.targets || previous.native.revision != currentNative.revision) {
                    return "nodes_changed"
                }
            } else {
                if (!MobileFrameStability.sameRegion(previous.frame, currentFrame, coordinateBounds)) return "frame_changed"
                if (localTargets(previous.native, coordinateBounds, currentFrame) !=
                    localTargets(currentNative, coordinateBounds, currentFrame)
                ) return "nodes_changed"
            }
        }
        return null
    }

    private fun localTargets(
        snapshot: MobileAccessibilitySnapshot, region: MobileBounds, frame: CapturedMobileFrame, swipe: Boolean = false,
    ): Map<String, MobileNodeTarget> = snapshot.targets.filterValues {
        val bounds = it.element.bounds
        bounds.left < region.right && bounds.right > region.left && bounds.top < region.bottom && bounds.bottom > region.top
    }.mapValues { (_, target) ->
        fun comparable(element: MobileElement): MobileElement {
            val bounds = element.bounds
            // 滚动容器可能汇总屏幕其他区域的文字；它自身没有点按动作时，汇总文字不代表目标变化。
            val containerSummary = element.scrollable && element.clickable != true && !element.editable && element.actions.isEmpty() &&
                bounds.left <= region.left.coerceAtLeast(0) && bounds.top <= region.top.coerceAtLeast(0) &&
                bounds.right >= region.right.coerceAtMost(frame.width) && bounds.bottom >= region.bottom.coerceAtMost(frame.height)
            return if (swipe || containerSummary) element.copy(text = null, description = null) else element
        }
        target.copy(element = comparable(target.element), ownElement = comparable(target.ownElement),
            clickElement = target.clickElement?.let(::comparable))
    }
}
