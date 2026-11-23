package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止打断误触发、首字缓冲丢失和跨会话音频泄漏的边界回归，并钉住起始阈值与确认帧数。 */
class VoiceActivitySegmenterTest {
    @Test
    fun shortNoiseDoesNotStartSpeechAndPreRollPreservesOpening() {
        var starts = 0
        val completed = mutableListOf<UtteranceSegment>()
        val segmenter = VoiceActivitySegmenter({ starts++ }, completed::add)
        repeat(4) { segmenter.accept(frame(1), .9f) }
        segmenter.accept(frame(2), .1f)
        assertEquals(0, starts)
        repeat(6) { segmenter.accept(frame(3), .9f) }
        assertEquals(1, starts)
        repeat(22) { segmenter.accept(frame(4), .1f) }
        assertEquals(1, completed.size)
        // 语句含起始确认前的 pre-roll(6 确认帧 + 5 更早帧,未触发驱逐)与句末 22 帧静默。
        assertEquals(33 * VoiceActivitySegmenter.FRAME_BYTES, completed.single().pcm16.size)
        assertEquals(1.toByte(), completed.single().pcm16.first())
    }

    @Test
    fun resetDropsIncompleteSpeechBeforeNextSession() {
        val completed = mutableListOf<UtteranceSegment>()
        val segmenter = VoiceActivitySegmenter({}, completed::add)
        repeat(5) { segmenter.accept(frame(1), .9f) }
        segmenter.reset()
        repeat(6) { segmenter.accept(frame(2), .9f) }
        repeat(22) { segmenter.accept(frame(2), .1f) }
        assertEquals(1, completed.size)
        assertTrue(completed.single().pcm16.all { it == 2.toByte() })
    }

    @Test
    fun continuousSpeechIsBoundedAndDoesNotProduceEmptyUtterances() {
        val completed = mutableListOf<UtteranceSegment>()
        val segmenter = VoiceActivitySegmenter({}, completed::add)
        repeat(1_000) { segmenter.accept(frame(1), .9f) }
        assertEquals(1, completed.size)
        assertTrue(completed.single().pcm16.size in 960_000..961_024)
    }

    /** 旧起始阈值 0.6 与不足 6 帧的确认都必须被拒;0.75 连续 6 帧才开轮。 */
    @Test
    fun subThresholdProbabilityOrShortStreakDoesNotStartSpeech() {
        var starts = 0
        val completed = mutableListOf<UtteranceSegment>()
        val segmenter = VoiceActivitySegmenter({ starts++ }, completed::add)
        repeat(30) { segmenter.accept(frame(1), .6f) }
        repeat(5) { segmenter.accept(frame(1), .9f) }
        segmenter.accept(frame(1), .1f)
        assertEquals("瞬态与远处人声不得开轮", 0, starts)
        assertTrue(completed.isEmpty())
        repeat(6) { segmenter.accept(frame(1), .75f) }
        assertEquals(1, starts)
    }

    /** 段画像与音频严格同窗,提交门按此判断是否值得送识别。 */
    @Test
    fun segmentCarriesVadProfileForCommitGate() {
        val completed = mutableListOf<UtteranceSegment>()
        val segmenter = VoiceActivitySegmenter({}, completed::add)
        repeat(8) { segmenter.accept(frame(100), .9f) }
        repeat(22) { segmenter.accept(frame(0), .1f) }
        val segment = completed.single()
        assertEquals(.9f, segment.peakProbability, 0.0001f)
        assertEquals(8, segment.speechFrameCount)
        assertEquals(8, segment.voicedFrameCount)
        assertEquals(30, segment.frameCount)
        assertTrue("峰值电平应来自有声帧", segment.peakLevel > 0f)
    }

    private fun frame(value: Int) = ByteArray(VoiceActivitySegmenter.FRAME_BYTES) { value.toByte() }
}
