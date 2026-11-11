package github.ponyhuang.gimi.data.voice.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 上行帧队列的边界：VAD 逐帧复用同一块缓冲，跨帧持有必须拷贝；队列有界且按序。
 */
class UplinkFrameQueueTest {

    @Test
    fun `enqueue copies the reused VAD frame buffer`() {
        val queue = UplinkFrameQueue(4)
        // 模拟 VAD：同一个数组被下一帧反复覆写。
        val reused = ByteArray(4)
        reused[0] = 1
        queue.enqueue(reused)
        reused[0] = 2
        queue.enqueue(reused)
        reused[0] = 3

        assertArrayEquals(byteArrayOf(1, 0, 0, 0), queue.poll())
        assertArrayEquals(byteArrayOf(2, 0, 0, 0), queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `queue preserves insertion order and drops oldest when full`() {
        val queue = UplinkFrameQueue(3)
        repeat(5) { index -> queue.enqueue(byteArrayOf(index.toByte())) }

        assertEquals(2, queue.poll()!![0].toInt())
        assertEquals(3, queue.poll()!![0].toInt())
        assertEquals(4, queue.poll()!![0].toInt())
        assertNull(queue.poll())
    }

    @Test
    fun `clear drops the whole backlog`() {
        val queue = UplinkFrameQueue(8)
        queue.enqueue(byteArrayOf(1))
        queue.enqueue(byteArrayOf(2))
        queue.clear()
        assertNull(queue.poll())
    }

    @Test
    fun `polled frames are independent copies`() {
        val queue = UplinkFrameQueue(2)
        val source = byteArrayOf(7)
        queue.enqueue(source)
        val polled = queue.poll()!!
        assertNotSame(source, polled)
        polled[0] = 9
        assertEquals(7, source[0].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive capacity is rejected`() {
        UplinkFrameQueue(0)
    }
}
