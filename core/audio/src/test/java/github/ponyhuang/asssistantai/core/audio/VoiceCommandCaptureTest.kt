package github.ponyhuang.asssistantai.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandCaptureTest {
    @Test
    fun captureCancelsWhenNoCommandSpeechStarts() {
        val capture = VoiceCommandCapture(preRoll = byteArrayOf(1, 2), startedAtMs = 0L)

        val result = capture.append(silentPcm(), VoiceCommandCapture.SPEECH_START_TIMEOUT_MS)

        assertEquals(CaptureDecision.Cancel, result)
    }

    @Test
    fun captureCompletesAfterSpeechFollowedBySilence() {
        val capture = VoiceCommandCapture(preRoll = byteArrayOf(1, 2), startedAtMs = 0L)
        assertEquals(CaptureDecision.Continue, capture.append(voicedPcm(), 100L))

        val result = capture.append(
            silentPcm(),
            100L + VoiceCommandCapture.SILENCE_TO_FINISH_MS,
        )

        assertTrue(result is CaptureDecision.Complete)
        assertTrue((result as CaptureDecision.Complete).pcm16.size > 2)
    }

    @Test
    fun preRollKeepsOnlyMostRecentChunks() {
        val buffer = PcmPreRollBuffer(maxBytes = 4)
        buffer.append(byteArrayOf(1, 2, 3))
        buffer.append(byteArrayOf(4, 5, 6))

        assertEquals(listOf<Byte>(4, 5, 6), buffer.snapshot().toList())
    }

    @Test
    fun confirmationCaptureCancelsAtConfiguredFifteenSecondDeadline() {
        val capture = VoiceCommandCapture(
            preRoll = byteArrayOf(),
            startedAtMs = 0L,
            speechStartTimeoutMs = 15_000L,
            maxCaptureMs = 15_000L,
        )

        assertEquals(CaptureDecision.Cancel, capture.append(silentPcm(), 15_000L))
    }

    @Test
    fun captureAdaptsThresholdToLoudBackgroundNoise() {
        val capture = VoiceCommandCapture(preRoll = byteArrayOf(), startedAtMs = 0L)
        // 背景噪声幅度 650 低于初始阈值 700 → 噪声底开始缓慢抬升。
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(650), 100L))
        // 800 仍低于自适应阈值，不误判为语音，也不会把噪声底瞬间抬到 800。
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(800), 200L))
        // 明显高于背景的真实语音可以触发。
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(2_500), 300L))

        val result = capture.append(
            silentPcm(),
            300L + VoiceCommandCapture.SILENCE_TO_FINISH_MS,
        )

        assertTrue(result is CaptureDecision.Complete)
    }

    @Test
    fun captureDoesNotRaiseNoiseFloorPastSoftSpeech() {
        val capture = VoiceCommandCapture(preRoll = byteArrayOf(), startedAtMs = 0L)
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(650), 100L))
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(1_000), 200L))

        val result = capture.append(
            silentPcm(),
            200L + VoiceCommandCapture.SILENCE_TO_FINISH_MS,
        )

        assertTrue(result is CaptureDecision.Complete)
    }

    @Test
    fun lowGainAudioIsTranscribedAtSpeechStartDeadline() {
        val capture = VoiceCommandCapture(preRoll = byteArrayOf(), startedAtMs = 0L)
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(200), 100L))

        val result = capture.append(
            pcmWithAmplitude(200),
            VoiceCommandCapture.SPEECH_START_TIMEOUT_MS,
        )

        assertTrue(result is CaptureDecision.Complete)
    }

    private fun silentPcm(): ByteArray = ByteArray(3_200)

    private fun voicedPcm(): ByteArray = pcmWithAmplitude(2_000)

    private fun pcmWithAmplitude(amplitude: Int): ByteArray = ByteArray(3_200).also { bytes ->
        for (index in bytes.indices step 2) {
            bytes[index] = amplitude.toByte()
            bytes[index + 1] = (amplitude ushr 8).toByte()
        }
    }
}
