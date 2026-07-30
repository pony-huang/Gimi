package github.ponyhuang.gimi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {

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
}
