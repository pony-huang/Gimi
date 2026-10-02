package github.ponyhuang.gimi.data.mobileuse

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
        if (!navigation && nativeElementId == null && !previous.pixelActionsAllowed) return "unstable_coordinate_target"
        if (nativeElementId != null && !previous.nativeActionsAllowed) return "unstable_native_target"
        if (!navigation && nativeElementId == null && !previous.frame.jpeg.contentEquals(currentFrame.jpeg)) return "frame_changed"
        if (nativeElementId != null && previous.native.targets[nativeElementId] == null) return "unknown_native_target"
        if (!currentNative.consistent || previous.native.status != currentNative.status ||
            (!navigation && nativeElementId == null && previous.native.revision != currentNative.revision) ||
            previous.native.windows != currentNative.windows ||
            previous.native.targets != currentNative.targets
        ) return "nodes_changed"
        return null
    }
}
