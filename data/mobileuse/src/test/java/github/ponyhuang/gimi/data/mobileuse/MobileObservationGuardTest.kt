package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileObservationGuardTest {
    private val frame = CapturedMobileFrame(1080, 2400, 100, 1, 1, byteArrayOf(1), intArrayOf(42))
    private val native = MobileAccessibilitySnapshot("available")
    private val guard = MobileObservationGuard().apply {
        record(MobileObservedScreen("observation", "owner", 4, 100, frame, native, emptyList()))
    }

    @Test
    fun observationBelongsToOneOwnerDisplayAndLatestId() {
        assertNull(guard.rejection("owner", 4, "observation", 200, frame, native))
        assertEquals("observation_mismatch", guard.rejection("other", 4, "observation", 200, frame, native))
        assertEquals("observation_mismatch", guard.rejection("owner", 5, "observation", 200, frame, native))
        assertEquals("observation_mismatch", guard.rejection("owner", 4, "old", 200, frame, native))
        assertEquals("observation_expired", guard.rejection("owner", 4, "observation", 60_101, frame, native))
    }

    @Test
    fun changedFrameOrWindowInvalidatesCoordinatesAndOcr() {
        val changed = CapturedMobileFrame(1080, 2400, 200, 2, 1, byteArrayOf(2), intArrayOf(42))
        assertEquals("frame_changed", guard.rejection("owner", 4, "observation", 200, changed, native))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "observation", 200, frame, native.copy(revision = 1)))
    }

    @Test
    fun attemptedActionCannotReuseItsObservation() {
        guard.clear()
        assertEquals("no_actionable_observation", guard.rejection("owner", 4, "observation", 200, frame, native))
    }

    @Test
    fun subsequentObserveMustStillWaitForPostActionFrame() {
        guard.awaitFrameAfter(200)
        guard.clear()
        guard.record(MobileObservedScreen("next", "owner", 4, 300, frame, native, emptyList()))
        assertEquals("no_post_action_frame", guard.rejection("owner", 4, "next", 300, frame, native))
        guard.confirmFrame(frame)
        assertEquals(200L, guard.requiredFrameAfterMs)
        val fresh = CapturedMobileFrame(1080, 2400, 400, 2, 1, byteArrayOf(1), intArrayOf(42))
        guard.confirmFrame(fresh)
        assertNull(guard.requiredFrameAfterMs)
        assertNull(guard.rejection("owner", 4, "next", 500, fresh, native))
    }

    @Test
    fun animationDoesNotPreventValidatedNativeClickButCoordinatesRemainBlocked() {
        val element = MobileElement("a1", "accessibility", MobileBounds(10, 20, 30, 40), actions = listOf("click"))
        val target = MobileNodeTarget(element, emptyList(), element, emptyList(), element)
        val snapshot = native.copy(targets = mapOf("a1" to target))
        guard.record(MobileObservedScreen("animated", "owner", 4, 100, frame, snapshot, listOf(element), pixelActionsAllowed = false))
        val animated = CapturedMobileFrame(1080, 2400, 200, 2, 1, byteArrayOf(2), intArrayOf(43))
        assertNull(guard.rejection("owner", 4, "animated", 200, animated, snapshot.copy(revision = 1), nativeElementId = "a1"))
        assertEquals("unstable_coordinate_target", guard.rejection("owner", 4, "animated", 200, animated, snapshot))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "animated", 200, animated, snapshot.copy(targets = emptyMap()), nativeElementId = "a1"))
        assertNull(guard.rejection("owner", 4, "animated", 200, animated, snapshot, navigation = true))
    }

    @Test
    fun timerUpdateDoesNotInvalidateUnchangedNativeButtonOrBack() {
        val button = MobileElement("pause", "accessibility", MobileBounds(10, 20, 30, 40), actions = listOf("click"))
        val timer = button.copy(id = "timer", text = "01:19", actions = emptyList())
        fun target(element: MobileElement) = MobileNodeTarget(element, emptyList(), element, emptyList(), element)
        val before = native.copy(targets = mapOf("pause" to target(button), "timer" to target(timer)))
        val after = before.copy(revision = 1, targets = before.targets + ("timer" to target(timer.copy(text = "01:20"))))
        guard.record(MobileObservedScreen("player", "owner", 4, 100, frame, before, listOf(button, timer)))
        assertNull(guard.rejection("owner", 4, "player", 200, frame, after, nativeElementId = "pause"))
        assertNull(guard.rejection("owner", 4, "player", 200, frame, after, navigation = true))
        val moved = after.copy(targets = after.targets + ("pause" to target(button.copy(bounds = MobileBounds(40, 20, 60, 40)))))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "player", 200, frame, moved, nativeElementId = "pause"))
    }

    @Test
    fun animatedWebViewAllowsSwipeAndStableCoordinateRegion() {
        val samples = IntArray(64 * 96) { 100 }
        val before = CapturedMobileFrame(1080, 2400, 100, 1, 1, byteArrayOf(1), samples)
        val animated = CapturedMobileFrame(1080, 2400, 200, 2, 1, byteArrayOf(2), samples.copyOf().apply { this[0] = 200 })
        val webView = MobileAccessibilitySnapshot("accessibility_unavailable")
        val region = MobileBounds(400, 1000, 500, 1100)
        guard.record(MobileObservedScreen("web", "owner", 4, 100, before, webView, emptyList(), swipeActionsAllowed = true))
        assertNull(guard.rejection("owner", 4, "web", 200, animated, webView, coordinateBounds = region, swipe = true))
        assertNull(guard.rejection("owner", 4, "web", 200, animated, webView, coordinateBounds = region))
        val changedTarget = CapturedMobileFrame(1080, 2400, 200, 3, 1, byteArrayOf(3), samples.copyOf().apply { this[42 * 64 + 26] = 200 })
        assertEquals("frame_changed", guard.rejection("owner", 4, "web", 200, changedTarget, webView, coordinateBounds = region))
        val overlay = webView.copy(windows = listOf(MobileWindowState(2, 2, MobileBounds(0, 0, 1080, 2400))))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "web", 200, animated, overlay, coordinateBounds = region, swipe = true))
        assertEquals("frame_changed", guard.rejection("owner", 4, "web", 200, CapturedMobileFrame(1080, 2400, 200, 3, 2, byteArrayOf(3), samples), webView, coordinateBounds = region, swipe = true))
        guard.awaitFrameAfter(200)
        assertEquals("no_post_action_frame", guard.rejection("owner", 4, "web", 200, animated, webView, coordinateBounds = region, swipe = true))
    }

    @Test
    fun unrelatedNodeUpdateDoesNotInvalidateCoordinateRegion() {
        val samples = IntArray(64 * 96) { 100 }
        val picture = CapturedMobileFrame(1080, 2400, 100, 1, 1, byteArrayOf(1), samples)
        val element = MobileElement("ad", "accessibility", MobileBounds(10, 20, 30, 40), text = "Ad 1")
        fun target(value: MobileElement) = MobileNodeTarget(value, emptyList(), value, null, null)
        val before = native.copy(targets = mapOf("ad" to target(element)))
        val after = before.copy(revision = 1, targets = mapOf("ad" to target(element.copy(text = "Ad 2"))))
        guard.record(MobileObservedScreen("page", "owner", 4, 100, picture, before, listOf(element)))
        assertNull(guard.rejection("owner", 4, "page", 200, picture, after, coordinateBounds = MobileBounds(400, 1000, 500, 1100)))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "page", 200, picture, after, coordinateBounds = element.bounds))
    }

    @Test
    fun scrollingContainerSummaryDoesNotInvalidateFixedCoordinateButton() {
        val samples = IntArray(64 * 96) { 100 }
        val picture = CapturedMobileFrame(1080, 2400, 100, 1, 1, byteArrayOf(1), samples)
        val region = MobileBounds(400, 1000, 500, 1100)
        val container = MobileElement("list", "accessibility", MobileBounds(0, 0, 1080, 2400),
            description = "广告 1，关注列表", scrollable = true)
        val button = MobileElement("button", "accessibility", region, text = "关注", actions = listOf("click"))
        fun target(value: MobileElement) = MobileNodeTarget(value, emptyList(), value, null, null)
        val before = native.copy(targets = mapOf("list" to target(container), "button" to target(button)))
        val after = before.copy(revision = 1,
            targets = before.targets + ("list" to target(container.copy(description = "广告 2，关注列表"))))
        guard.record(MobileObservedScreen("page", "owner", 4, 100, picture, before, listOf(container, button)))
        assertNull(guard.rejection("owner", 4, "page", 200, picture, after, coordinateBounds = region))
        val changedButton = after.copy(targets = after.targets + ("button" to target(button.copy(text = "购买"))))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "page", 200, picture, changedButton, coordinateBounds = region))
    }

    @Test
    fun swipeIgnoresFeedTextUpdatesButRejectsReplacementOfItsSurface() {
        val surface = MobileElement("list", "accessibility", MobileBounds(0, 0, 1080, 2400),
            resourceId = "app:id/feed", packageName = "app", scrollable = true, description = "广告 1")
        fun target(value: MobileElement) = MobileNodeTarget(value, emptyList(), value, null, null)
        val before = native.copy(targets = mapOf("list" to target(surface)))
        val updated = before.copy(revision = 1, targets = mapOf("list" to target(surface.copy(description = "广告 2"))))
        val replaced = updated.copy(targets = mapOf("list" to target(surface.copy(resourceId = "app:id/payment"))))
        guard.record(MobileObservedScreen("feed", "owner", 4, 100, frame, before, listOf(surface)))
        val region = MobileBounds(400, 1000, 500, 1100)
        assertNull(guard.rejection("owner", 4, "feed", 200, frame, updated, coordinateBounds = region, swipe = true))
        assertEquals("nodes_changed", guard.rejection("owner", 4, "feed", 200, frame, replaced, coordinateBounds = region, swipe = true))
    }

    @Test
    fun higherWindowMasksLowerElementButNotItsOwnElements() {
        val element = MobileElement("a", "accessibility", MobileBounds(100, 100, 200, 200), windowId = 1, windowLayer = 1)
        val overlay = MobileWindowState(2, 2, MobileBounds(0, 0, 1080, 2400))
        assertTrue(listOf(overlay).covers(element))
        assertFalse(listOf(overlay).covers(element.copy(windowId = 2, windowLayer = 2)))
        assertFalse(listOf(overlay.copy(bounds = MobileBounds(0, 0, 50, 50))).covers(element))
    }
}
