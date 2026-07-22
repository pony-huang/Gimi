package github.ponyhuang.asssistantai.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {

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

    @Test
    fun voiceBufferDrainsAndResetsWithoutTouchingDraftHelpers() {
        val buffer = VoicePcmBuffer()
        buffer.append(byteArrayOf(1, 2))
        buffer.append(byteArrayOf(3, 4))

        assertEquals(listOf<Byte>(1, 2, 3, 4), buffer.drain().toList())
        assertTrue(buffer.drain().isEmpty())

        buffer.append(byteArrayOf(5, 6))
        buffer.reset()
        assertTrue(buffer.drain().isEmpty())
    }

    @Test
    fun transcriptIsAppendedOnlyWhenRecognitionReturnsText() {
        assertEquals("原草稿 识别文字", appendTranscript("原草稿", " 识别文字 "))
        assertEquals("原草稿", appendTranscript("原草稿", "  "))
        assertEquals("识别文字", appendTranscript("", "识别文字"))
    }

    @Test
    fun recordingTimeUsesMinuteAndPaddedSeconds() {
        assertEquals("1:00", formatRecordingTime(60))
        assertEquals("0:09", formatRecordingTime(9))
        assertEquals("0:00", formatRecordingTime(-1))
    }

    private fun pcm16(vararg samples: Int): ByteArray = buildList {
        samples.forEach { sample ->
            val value = sample.toShort().toInt()
            add((value and 0xff).toByte())
            add(((value shr 8) and 0xff).toByte())
        }
    }.toByteArray()
}
