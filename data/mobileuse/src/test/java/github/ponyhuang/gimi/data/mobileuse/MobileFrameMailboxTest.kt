package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileFrameMailboxTest {
    @Test
    fun continuousFramesKeepOneScheduledEncodeAndRetainFinalFrame() {
        val mailbox = MobileFrameMailbox<Int>()
        val released = mutableListOf<Int>()
        assertEquals(0L, mailbox.offer(1, 0) { released += it })
        assertEquals(1, mailbox.take(0))
        assertEquals(134L, mailbox.offer(2, 16) { released += it })
        assertEquals(-1L, mailbox.offer(3, 32) { released += it })
        assertEquals(-1L, mailbox.offer(4, 144) { released += it })
        assertEquals(listOf(2, 3), released)
        assertEquals(4, mailbox.take(150))
        assertEquals(150L, mailbox.offer(5, 150) { released += it })
    }

    @Test
    fun resettingSurfaceReleasesPendingImageAndRestartsCadence() {
        val mailbox = MobileFrameMailbox<Int>()
        val released = mutableListOf<Int>()
        mailbox.offer(1, 0) { released += it }
        mailbox.reset { released += it }
        assertEquals(listOf(1), released)
        assertNull(mailbox.take(100))
        mailbox.reset { released += it }
        assertEquals(0L, mailbox.offer(2, 100) { released += it })
    }
}
