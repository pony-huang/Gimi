package github.ponyhuang.gimi.data.mobileuse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileCaptureRecoveryTest {
    @Test
    fun retriesMissingFrameWithoutRecreatingDisplay() {
        val frame = byteArrayOf(1, 2, 3)
        var attempts = 0
        var recoveries = 0
        val result = MobileCaptureRecovery.capture(
            captureFrame = { if (++attempts == 3) frame else null },
            recoverSurface = { recoveries++ },
        )
        assertArrayEquals(frame, result)
        assertEquals(3, attempts)
        assertEquals(1, recoveries)
    }

    @Test
    fun reportsMissingFrameAfterSurfaceRecovery() {
        var recoveries = 0
        val result = MobileCaptureRecovery.capture(
            captureFrame = { null },
            recoverSurface = { recoveries++ },
        )
        assertNull(result)
        assertEquals(1, recoveries)
    }
}
