package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回声参考的时间对齐、停播截断与长通话环形缓冲回归。 */
class PcmPlaybackReferenceTest {
    @Test
    fun hardwareClockSelectsPlayedIntervalInsteadOfLatestQueuedAudio() {
        val reference = PcmPlaybackReference()
        reference.append(pcm(960), 0, 1920)
        reference.anchor(240, 1_000_000_000)
        val output = ShortArray(240)
        reference.mixInto(990_000_000, output)
        assertEquals(1, output.first().toInt())
        assertEquals(240, output.last().toInt())
    }

    @Test
    fun stopRetainsAudibleTailAndExcludesFlushedFutureSamples() {
        val reference = PcmPlaybackReference()
        reference.append(pcm(960), 0, 1920)
        reference.anchor(240, 1_000_000_000)
        reference.stop(300)
        val output = ShortArray(240)
        reference.mixInto(1_000_000_000, output)
        assertEquals(241, output.first().toInt())
        assertEquals(300, output[59].toInt())
        assertTrue(output.drop(60).all { it == 0.toShort() })
    }

    @Test
    fun overwrittenHistoryAndGapsReturnSilenceWithoutReplayingOldPcm() {
        val reference = PcmPlaybackReference()
        val bytes = ByteArray(48_240 * 2) { if (it % 2 == 0) 1 else 0 }
        reference.append(bytes, 0, bytes.size)
        reference.anchor(0, 1_000_000_000)
        val overwritten = ShortArray(240)
        reference.mixInto(1_000_000_000, overwritten)
        assertTrue(overwritten.all { it == 0.toShort() })
        val retained = ShortArray(240)
        reference.mixInto(1_010_000_000, retained)
        assertTrue(retained.all { it == 1.toShort() })
        val future = ShortArray(240)
        reference.mixInto(4_000_000_000, future)
        assertTrue(future.all { it == 0.toShort() })
    }

    private fun pcm(samples: Int): ByteArray = ByteArray(samples * 2).also { bytes ->
        repeat(samples) { index ->
            bytes[index * 2] = (index + 1).toByte()
            bytes[index * 2 + 1] = ((index + 1) shr 8).toByte()
        }
    }
}
