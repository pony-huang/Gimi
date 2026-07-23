package github.ponyhuang.asssistantai.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmLevelTest {

    @Test
    fun pcmLevelReturnsZeroForEmptySilentAndIncompleteAudio() {
        assertEquals(0f, calculatePcm16Level(byteArrayOf()), 0f)
        assertEquals(0f, calculatePcm16Level(byteArrayOf(0, 0, 0, 0)), 0f)
        assertEquals(0f, calculatePcm16Level(byteArrayOf(42)), 0f)
    }

    @Test
    fun pcmLevelIncreasesWithSignalStrengthAndStaysNormalized() {
        val quiet = calculatePcm16Level(pcm16(512, -512, 512, -512))
        val medium = calculatePcm16Level(pcm16(8_192, -8_192, 8_192, -8_192))
        val loud = calculatePcm16Level(pcm16(Short.MAX_VALUE.toInt(), Short.MIN_VALUE.toInt()))

        assertTrue(quiet in 0f..1f)
        assertTrue(medium > quiet)
        assertTrue(loud > medium)
        assertTrue(loud <= 1f)
    }

    private fun pcm16(vararg samples: Int): ByteArray = buildList {
        samples.forEach { sample ->
            val value = sample.toShort().toInt()
            add((value and 0xff).toByte())
            add(((value shr 8) and 0xff).toByte())
        }
    }.toByteArray()
}
