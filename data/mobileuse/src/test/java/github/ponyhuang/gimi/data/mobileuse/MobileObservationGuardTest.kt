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
    fun higherWindowMasksLowerElementButNotItsOwnElements() {
        val element = MobileElement("a", "accessibility", MobileBounds(100, 100, 200, 200), windowId = 1, windowLayer = 1)
        val overlay = MobileWindowState(2, 2, MobileBounds(0, 0, 1080, 2400))
        assertTrue(listOf(overlay).covers(element))
        assertFalse(listOf(overlay).covers(element.copy(windowId = 2, windowLayer = 2)))
        assertFalse(listOf(overlay.copy(bounds = MobileBounds(0, 0, 50, 50))).covers(element))
    }
}
