package github.ponyhuang.asssistantai.voice

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
    fun confirmationOnlyAcceptsExplicitAllowWords() {
        assertTrue(isVoiceConfirmationApproved("确认执行"))
        assertTrue(isVoiceConfirmationApproved("允许"))
        assertTrue(isVoiceConfirmationApproved("执行"))
    }

    @Test
    fun rejectionWordsTakePriorityAndAmbiguousSpeechFailsClosed() {
        assertEquals(false, isVoiceConfirmationApproved("不要执行"))
        assertEquals(false, isVoiceConfirmationApproved("取消"))
        assertEquals(false, isVoiceConfirmationApproved("好的"))
        assertEquals(false, isVoiceConfirmationApproved(""))
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

    private fun silentPcm(): ByteArray = ByteArray(3_200)

    private fun voicedPcm(): ByteArray = ByteArray(3_200).also { bytes ->
        val sample = 2_000
        for (index in bytes.indices step 2) {
            bytes[index] = sample.toByte()
            bytes[index + 1] = (sample ushr 8).toByte()
        }
    }
}
