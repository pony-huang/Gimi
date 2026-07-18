package github.ponyhuang.asssistantai.voice

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.abs

internal class PcmPreRollBuffer(
    private val maxBytes: Int = BluetoothPcmRecorder.SAMPLE_RATE_HZ * 2 * 5 / 2,
) {
    private val chunks = ArrayDeque<ByteArray>()
    private var size = 0

    @Synchronized
    fun append(chunk: ByteArray) {
        chunks.addLast(chunk.copyOf())
        size += chunk.size
        while (size > maxBytes && chunks.isNotEmpty()) {
            size -= chunks.removeFirst().size
        }
    }

    @Synchronized
    fun snapshot(): ByteArray = ByteArrayOutputStream(size).also { output ->
        chunks.forEach(output::write)
    }.toByteArray()
}

internal class VoiceCommandCapture(
    preRoll: ByteArray,
    private val startedAtMs: Long,
) {
    private val output = ByteArrayOutputStream().apply { write(preRoll) }
    private var speechDetected = false
    private var lastSpeechAtMs = startedAtMs

    fun append(chunk: ByteArray, nowMs: Long): CaptureDecision {
        output.write(chunk)
        if (isSpeech(chunk)) {
            speechDetected = true
            lastSpeechAtMs = nowMs
        }
        return when {
            nowMs - startedAtMs >= MAX_CAPTURE_MS -> CaptureDecision.Complete(output.toByteArray())
            !speechDetected && nowMs - startedAtMs >= SPEECH_START_TIMEOUT_MS -> CaptureDecision.Cancel
            speechDetected && nowMs - lastSpeechAtMs >= SILENCE_TO_FINISH_MS ->
                CaptureDecision.Complete(output.toByteArray())
            else -> CaptureDecision.Continue
        }
    }

    private fun isSpeech(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        var sum = 0L
        var samples = 0
        var index = 0
        while (index + 1 < bytes.size) {
            val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            sum += abs(sample.toShort().toInt())
            samples++
            index += 2
        }
        return samples > 0 && sum / samples >= SPEECH_AMPLITUDE_THRESHOLD
    }

    companion object {
        const val SPEECH_START_TIMEOUT_MS = 5_000L
        const val SILENCE_TO_FINISH_MS = 1_200L
        const val MAX_CAPTURE_MS = 30_000L
        const val SPEECH_AMPLITUDE_THRESHOLD = 700L
    }
}

internal sealed interface CaptureDecision {
    data object Continue : CaptureDecision
    data object Cancel : CaptureDecision
    data class Complete(val pcm16: ByteArray) : CaptureDecision
}
