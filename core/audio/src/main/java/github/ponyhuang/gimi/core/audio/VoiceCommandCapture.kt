package github.ponyhuang.gimi.core.audio

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.abs

private const val PRE_ROLL_MAX_BYTES = VoiceAudioRecorder.SAMPLE_RATE_HZ * 2 * 5 / 2

class PcmPreRollBuffer(
    private val maxBytes: Int = PRE_ROLL_MAX_BYTES,
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

class VoiceCommandCapture(
    preRoll: ByteArray,
    private val startedAtMs: Long,
    private val speechStartTimeoutMs: Long = SPEECH_START_TIMEOUT_MS,
    private val maxCaptureMs: Long = MAX_CAPTURE_MS,
) {
    private val output = ByteArrayOutputStream().apply { write(preRoll) }
    private var speechDetected = false
    private var lastSpeechAtMs = startedAtMs
    private var noiseFloor = 0.0

    fun append(chunk: ByteArray, nowMs: Long): CaptureDecision {
        output.write(chunk)
        if (isSpeech(chunk)) {
            speechDetected = true
            lastSpeechAtMs = nowMs
        }
        return when {
            nowMs - startedAtMs >= maxCaptureMs -> if (speechDetected) {
                CaptureDecision.Complete(output.toByteArray())
            } else {
                CaptureDecision.Cancel
            }
            !speechDetected && nowMs - startedAtMs >= speechStartTimeoutMs -> CaptureDecision.Cancel
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
        if (samples == 0) return false
        val average = sum / samples
        // 不同麦克风增益差异大，固定阈值不可靠。噪声底只允许缓慢上升，
        // 避免把一段较轻的人声立即吸收到噪声底中，导致后续整句都无法触发。
        val threshold = (noiseFloor * NOISE_SPEECH_FACTOR)
            .coerceIn(SPEECH_AMPLITUDE_THRESHOLD.toDouble(), MAX_SPEECH_AMPLITUDE.toDouble())
        if (average >= threshold) return true
        noiseFloor = if (average > noiseFloor) {
            noiseFloor + (average - noiseFloor) * NOISE_FLOOR_RISE_ALPHA
        } else {
            maxOf(average.toDouble(), noiseFloor * NOISE_FLOOR_DECAY)
        }
        return false
    }

    companion object {
        const val SPEECH_START_TIMEOUT_MS = 5_000L
        const val SILENCE_TO_FINISH_MS = 1_200L
        const val MAX_CAPTURE_MS = 30_000L
        const val SPEECH_AMPLITUDE_THRESHOLD = 700L
        const val MAX_SPEECH_AMPLITUDE = 3_000L
        const val NOISE_SPEECH_FACTOR = 1.5
        const val NOISE_FLOOR_RISE_ALPHA = 0.05
        const val NOISE_FLOOR_DECAY = 0.98
    }
}

sealed interface CaptureDecision {
    data object Continue : CaptureDecision
    data object Cancel : CaptureDecision
    data class Complete(val pcm16: ByteArray) : CaptureDecision
}
