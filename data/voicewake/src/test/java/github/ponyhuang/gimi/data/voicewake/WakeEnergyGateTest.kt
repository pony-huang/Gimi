package github.ponyhuang.gimi.data.voicewake

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeEnergyGateTest {
    private val gate = WakeEnergyGate()

    @Test
    fun silenceChunksAreSkipped() {
        repeat(10) {
            assertFalse(gate.shouldFeed(silence(1600)))
        }
    }

    @Test
    fun loudChunkIsFedAndStartsHangover() {
        assertTrue(gate.shouldFeed(tone(1600, amplitude = 5_000)))
        // 拖尾窗口内的静音块仍喂入，保证词尾与识别端点处理。
        repeat(25) {
            assertTrue(gate.shouldFeed(silence(1600)))
        }
        // 拖尾耗尽后恢复跳过。
        assertFalse(gate.shouldFeed(silence(1600)))
    }

    @Test
    fun hangoverRefreshesOnEachLoudChunk() {
        assertTrue(gate.shouldFeed(tone(1600, amplitude = 5_000)))
        repeat(10) { assertTrue(gate.shouldFeed(silence(1600))) }
        assertTrue(gate.shouldFeed(tone(1600, amplitude = 5_000)))
        repeat(25) { assertTrue(gate.shouldFeed(silence(1600))) }
        assertFalse(gate.shouldFeed(silence(1600)))
    }

    @Test
    fun resetDropsPendingHangover() {
        assertTrue(gate.shouldFeed(tone(1600, amplitude = 5_000)))
        gate.reset()
        assertFalse(gate.shouldFeed(silence(1600)))
    }

    @Test
    fun nearSilenceBelowThresholdIsSkipped() {
        // 幅度 100 的正弦远低于阈值，应被判定为静音。
        assertFalse(gate.shouldFeed(tone(1600, amplitude = 100)))
    }

    @Test
    fun oddSizedChunkIsHandledWithoutCrash() {
        assertFalse(gate.shouldFeed(silence(1_601)))
        assertTrue(gate.shouldFeed(tone(1_601, amplitude = 5_000)))
    }

    @Test
    fun emptyChunkIsSkipped() {
        assertFalse(gate.shouldFeed(ByteArray(0)))
    }

    /** 与运行时一致的 100ms/1600 样本块；每块 100ms 对应拖尾 25 块 = 2.5s。 */
    private fun silence(samples: Int): ByteArray = ByteArray(samples * 2)

    private fun tone(samples: Int, amplitude: Int): ByteArray {
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (amplitude * sin(2.0 * PI * i / 32.0)).toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
