package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MobileFrameBufferTest {
    @Test
    fun unchangedDisplayKeepsLatestCompleteFrame() {
        val buffer = MobileFrameBuffer()
        val generation = buffer.reset()
        buffer.publish(generation, 1080, 2400, 100, byteArrayOf(1), intArrayOf(42))
        val frame = buffer.latest()
        assertSame(frame, buffer.latest())
        assertEquals(100L, frame.capturedAtMs)
        assertEquals(1080, frame.width)
    }

    @Test
    fun changingSurfaceDiscardsCacheAndLateFrames() {
        val buffer = MobileFrameBuffer()
        val oldGeneration = buffer.reset()
        buffer.publish(oldGeneration, 1080, 2400, 100, byteArrayOf(1), intArrayOf(42))
        val generation = buffer.reset()
        assertNull(buffer.latest())
        buffer.publish(oldGeneration, 1080, 2400, 200, byteArrayOf(2), intArrayOf(43))
        assertNull(buffer.latest())
        buffer.publish(generation, 2400, 1080, 300, byteArrayOf(3), intArrayOf(44))
        assertEquals(2400, buffer.latest().width)
        assertEquals(2L, buffer.latest().sequence)
    }
}
