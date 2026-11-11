package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止打断误触发、首字缓冲丢失和跨会话音频泄漏的边界回归。 */
class VoiceActivitySegmenterTest {
    @Test
    fun shortNoiseDoesNotStartSpeechAndPreRollPreservesOpening() {
        var starts = 0
        val completed = mutableListOf<ByteArray>()
        val segmenter = VoiceActivitySegmenter({ starts++ }, completed::add)
        repeat(4) { segmenter.accept(frame(1), .9f) }
        segmenter.accept(frame(2), .1f)
        assertEquals(0, starts)
        repeat(5) { segmenter.accept(frame(3), .9f) }
        assertEquals(1, starts)
        repeat(22) { segmenter.accept(frame(4), .1f) }
        assertEquals(1, completed.size)
        assertEquals(32 * VoiceActivitySegmenter.FRAME_BYTES, completed.single().size)
        assertEquals(1.toByte(), completed.single().first())
    }

    @Test
    fun resetDropsIncompleteSpeechBeforeNextSession() {
        val completed = mutableListOf<ByteArray>()
        val segmenter = VoiceActivitySegmenter({}, completed::add)
        repeat(5) { segmenter.accept(frame(1), .9f) }
        segmenter.reset()
        repeat(5) { segmenter.accept(frame(2), .9f) }
        repeat(22) { segmenter.accept(frame(2), .1f) }
        assertEquals(1, completed.size)
        assertTrue(completed.single().all { it == 2.toByte() })
    }

    @Test
    fun continuousSpeechIsBoundedAndDoesNotProduceEmptyUtterances() {
        val completed = mutableListOf<ByteArray>()
        val segmenter = VoiceActivitySegmenter({}, completed::add)
        repeat(1_000) { segmenter.accept(frame(1), .9f) }
        assertEquals(1, completed.size)
        assertTrue(completed.single().size in 960_000..961_024)
    }

    private fun frame(value: Int) = ByteArray(VoiceActivitySegmenter.FRAME_BYTES) { value.toByte() }
}
