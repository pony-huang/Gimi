package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 升采样器的契约：分块调用与整段调用产出一致、常量与斜坡信号形状保持、
 * 末尾 flush 补齐到理论输出总量。上行音频质量依赖这三条，属于必要风险边界。
 */
class Pcm16ResamplerTest {

    private fun samplesOf(vararg values: Short): ByteArray {
        val bytes = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            val v = value.toInt()
            bytes[index * 2] = (v and 0xFF).toByte()
            bytes[index * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun readShort(bytes: ByteArray, index: Int): Short =
        (((bytes[index * 2 + 1].toInt() and 0xFF) shl 8) or (bytes[index * 2].toInt() and 0xFF)).toShort()

    @Test
    fun `constant input stays constant`() {
        val resampler = Pcm16Resampler(inputRateHz = 16_000, outputRateHz = 24_000)
        val output = resampler.process(samplesOf(1000, 1000, 1000, 1000)) + resampler.flush()
        assertTrue(output.isNotEmpty())
        for (i in 0 until output.size / 2) {
            assertEquals("sample $i", 1000.toShort(), readShort(output, i))
        }
    }

    @Test
    fun `chunked processing matches whole-buffer processing`() {
        val whole = samplesOf(0, 300, 700, 1200, 900, 400, -200, -800, -300, 100)
        val expected = Pcm16Resampler(16_000, 24_000).let { it.process(whole) + it.flush() }

        val chunked = Pcm16Resampler(16_000, 24_000)
        var actual = ByteArray(0)
        for (start in 0 until whole.size / 2 step 3) {
            val end = minOf(start + 3, whole.size / 2) * 2
            actual += chunked.process(whole.copyOfRange(start * 2, end))
        }
        actual += chunked.flush()

        assertEquals(expected.size, actual.size)
        assertTrue(expected.contentEquals(actual))
    }

    @Test
    fun `flush completes output to the theoretical count`() {
        val resampler = Pcm16Resampler(16_000, 24_000)
        val inputSamples = 7
        val output = resampler.process(samplesOf(*ShortArray(inputSamples) { 0 })) + resampler.flush()
        // ceil(7 * 24 / 16) = 11
        assertEquals(11, output.size / 2)
    }

    @Test
    fun `odd byte input is rejected`() {
        val resampler = Pcm16Resampler(16_000, 24_000)
        try {
            resampler.process(ByteArray(3))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals("PCM16 输入必须是完整采样（偶数字节）", expected.message)
        }
    }
}
