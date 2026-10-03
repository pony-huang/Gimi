package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileFrameStabilityTest {
    private fun frame(time: Long = 100, samples: IntArray = IntArray(64 * 96) { 100 }) =
        CapturedMobileFrame(1080, 2400, time, 1, 1, byteArrayOf(1), samples)

    @Test
    fun cachedStaticFrameCanSettleDuringObservation() {
        val stability = MobileFrameStability(100)
        assertFalse(stability.update(frame(), 100, 0))
        assertTrue(stability.update(frame(), 600, 0))
    }

    @Test
    fun preActionFrameDoesNotProveActionWasRendered() {
        val stability = MobileFrameStability(200, afterActionMs = 200)
        stability.update(frame(), 200, 0)
        assertFalse(stability.update(frame(), 900, 0))
        assertEquals("no_post_action_frame", stability.timeoutReason())
        assertTrue(stability.update(frame(time = 300), 900, 0))
    }

    @Test
    fun localChangeAndTargetWindowEventResetQuietPeriod() {
        val stability = MobileFrameStability(100)
        stability.update(frame(), 100, 0)
        val changed = IntArray(64 * 96) { 100 }.apply { for (index in 0..15) this[index] = 255 }
        assertFalse(stability.update(frame(samples = changed), 650, 0))
        assertFalse(stability.update(frame(samples = changed), 1200, 1100))
        assertTrue(stability.update(frame(samples = changed), 1600, 1100))
    }

    @Test
    fun regionComparisonClipsScreenEdgesAndRejectsChangedTargetPixels() {
        val before = frame()
        val region = MobileBounds(-48, -48, 49, 49)
        assertTrue(MobileFrameStability.sameRegion(before, frame(samples = before.samples.copyOf().apply { this[lastIndex] = 200 }), region))
        assertFalse(MobileFrameStability.sameRegion(before, frame(samples = before.samples.copyOf().apply { this[0] = 200 }), region))
        assertFalse(MobileFrameStability.sameRegion(before, before, MobileBounds(-50, 0, 0, 10)))
        assertFalse(MobileFrameStability.sameRegion(before, before, MobileBounds(1080, 0, 1100, 10)))
        assertFalse(MobileFrameStability.sameRegion(before, before, MobileBounds(10, 20, 10, 30)))
        val missingSamples = frame(samples = intArrayOf())
        assertFalse(MobileFrameStability.sameRegion(missingSamples, missingSamples, MobileBounds(1080, 0, 1100, 10)))
    }

    @Test
    fun pureWhiteAndMissingFrameRemainExplicitOnTimeout() {
        val stability = MobileFrameStability(100)
        assertFalse(stability.update(null, 100, 0))
        assertEquals("no_frame", stability.timeoutReason())
        assertFalse(stability.update(frame(samples = IntArray(64 * 96) { 255 }), 900, 0))
        assertEquals("suspected_blank", stability.timeoutReason())
    }
}
