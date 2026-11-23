package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提交门的画像与电平判据：不达标的段在识别前丢弃，省成本并挡边缘噪声段。 */
class UtteranceCommitGateTest {

    private fun segment(
        peakProbability: Float = 0.95f,
        speechFrameCount: Int = 20,
        voicedFrameCount: Int = 24,
        frameCount: Int = 58,
        peakLevel: Float = 0.5f,
    ) = UtteranceSegment(ByteArray(0), peakProbability, speechFrameCount, voicedFrameCount, frameCount, peakLevel)

    @Test
    fun clearNearEndSpeechIsCommitted() {
        assertTrue(UtteranceCommitGate.allows(segment(), noiseFloor = 0.1f))
    }

    /** hi/hello/你好 级别的短问候：占比只对非静默帧计算，不再被句末静默尾稀释。 */
    @Test
    fun shortGreetingIsCommitted() {
        val greeting = segment(
            peakProbability = 0.95f,
            speechFrameCount = 7,
            voicedFrameCount = 10,
            frameCount = 44,
            peakLevel = 0.4f,
        )
        assertTrue(UtteranceCommitGate.allows(greeting, noiseFloor = 0.1f))
    }

    @Test
    fun tailSilenceDoesNotDiluteTheRatio() {
        // 与短问候完全相同的语音内容,句末静默更长(慢挂断/更宽松的结束):仍应提交。
        val greeting = segment(
            peakProbability = 0.95f,
            speechFrameCount = 7,
            voicedFrameCount = 10,
            frameCount = 60,
            peakLevel = 0.4f,
        )
        assertTrue(UtteranceCommitGate.allows(greeting, noiseFloor = 0.1f))
    }

    @Test
    fun missingNoiseFloorFallsBackToProfileOnly() {
        assertTrue("底噪未建立时退化为纯画像判定", UtteranceCommitGate.allows(segment(peakLevel = 0f), noiseFloor = null))
    }

    @Test
    fun lowPeakProbabilityIsRejected() {
        assertFalse(UtteranceCommitGate.allows(segment(peakProbability = 0.85f), noiseFloor = 0.1f))
    }

    @Test
    fun tooFewSpeechFramesAreRejected() {
        assertFalse(UtteranceCommitGate.allows(segment(speechFrameCount = 5), noiseFloor = 0.1f))
    }

    @Test
    fun dilutedSpeechRatioIsRejected() {
        // 12/30 = 0.4 < 0.5：非静默帧里大量低置信帧夹着少量高置信帧，典型的噪声段画像。
        assertFalse(
            UtteranceCommitGate.allows(
                segment(speechFrameCount = 12, voicedFrameCount = 30),
                noiseFloor = 0.1f,
            ),
        )
    }

    @Test
    fun levelCloseToNoiseFloorIsRejected() {
        assertFalse("远处人群声电平贴着底噪", UtteranceCommitGate.allows(segment(peakLevel = 0.15f), noiseFloor = 0.1f))
    }

    @Test
    fun emptySegmentIsRejected() {
        assertFalse(UtteranceCommitGate.allows(segment(frameCount = 0, voicedFrameCount = 0), noiseFloor = null))
        assertFalse(UtteranceCommitGate.allows(segment(frameCount = 44, voicedFrameCount = 0), noiseFloor = null))
    }
}
