package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底噪估计的学习边界：语音帧不学入底噪、污染帧被排除、瞬态限幅、极端噪声封顶。 */
class AmbientNoiseFloorTrackerTest {

    @Test
    fun floorIsEstablishedFromSilenceFrames() {
        val tracker = AmbientNoiseFloorTracker()
        assertNull(tracker.floor())
        repeat(30) { tracker.accept(level = 0.02f, speechProbability = 0.01f, contaminated = false) }
        assertEquals(0.02f, tracker.floor()!!, 0.005f)
    }

    @Test
    fun speechFramesDoNotRaiseFloor() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(30) { tracker.accept(0.05f, 0.01f, false) }
        repeat(60) { tracker.accept(0.8f, 0.95f, false) }
        assertEquals("贴嘴语音不能被学进底噪", 0.05f, tracker.floor()!!, 0.01f)
    }

    @Test
    fun babbleGapFramesRaiseFloorTowardAmbientLevel() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(10) { tracker.accept(0.05f, 0.01f, false) }
        // 人群声的音节间隙：概率低、电平即环境底噪。限幅 0.02/帧 → 30 帧后应接近 0.2。
        repeat(30) { tracker.accept(0.2f, 0.3f, false) }
        val floor = tracker.floor()!!
        assertTrue("底噪应向人群环境电平收敛(实际 $floor)", floor in 0.14f..0.21f)
    }

    @Test
    fun contaminatedFramesAreIgnored() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(30) { tracker.accept(0.05f, 0.01f, false) }
        repeat(60) { tracker.accept(0.6f, 0.01f, true) }
        assertEquals("播放期回声电平不得污染底噪", 0.05f, tracker.floor()!!, 0.01f)
    }

    @Test
    fun transientSpikesDoNotJumpTheFloor() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(30) { tracker.accept(0.05f, 0.01f, false) }
        tracker.accept(1f, 0.1f, false) // 关门声单帧尖峰
        assertEquals("单帧尖峰最多抬 0.02", 0.07f, tracker.floor()!!, 0.001f)
    }

    @Test
    fun floorIsCappedUnderExtremeNoise() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(200) { tracker.accept(1f, 0.1f, false) }
        assertEquals(AmbientNoiseFloorTracker.MAX_FLOOR, tracker.floor()!!, 0.001f)
    }

    @Test
    fun resetDropsEstimate() {
        val tracker = AmbientNoiseFloorTracker()
        repeat(30) { tracker.accept(0.05f, 0.01f, false) }
        tracker.reset()
        assertNull(tracker.floor())
    }
}
