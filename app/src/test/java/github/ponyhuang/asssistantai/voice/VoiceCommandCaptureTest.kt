package github.ponyhuang.asssistantai.voice

import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
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
        val (confirm, reject) = WakeModelCatalog.Chinese.let { it.confirmWords to it.rejectWords }
        assertTrue(isVoiceConfirmationApproved("确认执行", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("允许", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("执行", confirm, reject))
    }

    @Test
    fun rejectionWordsTakePriorityAndAmbiguousSpeechFailsClosed() {
        val (confirm, reject) = WakeModelCatalog.Chinese.let { it.confirmWords to it.rejectWords }
        assertEquals(false, isVoiceConfirmationApproved("不要执行", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("取消", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("好的", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("", confirm, reject))
    }

    @Test
    fun englishConfirmationUsesEnglishWordLists() {
        val (confirm, reject) = WakeModelCatalog.English.let { it.confirmWords to it.rejectWords }
        assertTrue(isVoiceConfirmationApproved("confirm", confirm, reject))
        assertTrue(isVoiceConfirmationApproved("yes, proceed", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("cancel", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("don't do it", confirm, reject))
        assertEquals(false, isVoiceConfirmationApproved("maybe", confirm, reject))
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
        // 背景噪声幅度 650 低于初始阈值 700 → 噪声底抬升，阈值升至约 1950
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(650), 100L))
        // 800 高于旧固定阈值 700，但低于自适应阈值，不误判为语音（噪声底随之升到 800）
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(800), 200L))
        // 真实语音高于自适应阈值（约 2400）
        assertEquals(CaptureDecision.Continue, capture.append(pcmWithAmplitude(2_500), 300L))

        val result = capture.append(
            silentPcm(),
            300L + VoiceCommandCapture.SILENCE_TO_FINISH_MS,
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
