package github.ponyhuang.asssistantai.core.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/** Captures microphone input as 16 kHz, mono, signed 16-bit PCM chunks. */
class VoiceAudioRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var readJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    fun start(
        onAudioChunk: (ByteArray) -> Unit,
        onAudioLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (audioRecord != null) return false

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize <= 0) {
            onError(IllegalStateException("Unable to determine an audio-recording buffer size."))
            return false
        }

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (error: Throwable) {
            onError(error)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError(IllegalStateException("Unable to initialize microphone recording."))
            return false
        }

        return try {
            recorder.startRecording()
            audioRecord = recorder
            readJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                var lastLevelDispatchMs = 0L
                var smoothedLevel = 0f
                while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when {
                        bytesRead > 0 -> {
                            val chunk = buffer.copyOf(bytesRead)
                            onAudioChunk(chunk)
                            val measuredLevel = calculatePcm16Level(chunk)
                            smoothedLevel = (smoothedLevel * LEVEL_SMOOTHING) +
                                (measuredLevel * (1f - LEVEL_SMOOTHING))
                            val nowMs = SystemClock.elapsedRealtime()
                            if (nowMs - lastLevelDispatchMs >= LEVEL_DISPATCH_INTERVAL_MS) {
                                lastLevelDispatchMs = nowMs
                                onAudioLevel(smoothedLevel)
                            }
                        }
                        bytesRead < 0 -> {
                            onError(IllegalStateException("Microphone read failed: $bytesRead"))
                            break
                        }
                    }
                }
            }
            true
        } catch (error: Throwable) {
            recorder.release()
            onError(error)
            false
        }
    }

    @Synchronized
    fun stop() {
        readJob?.cancel()
        readJob = null
        audioRecord?.run {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            release()
        }
        audioRecord = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val LEVEL_DISPATCH_INTERVAL_MS = 50L
        const val LEVEL_SMOOTHING = 0.65f
    }
}

/** Converts little-endian signed PCM16 audio into a perceptual 0..1 RMS level. */
fun calculatePcm16Level(bytes: ByteArray): Float {
    val sampleCount = bytes.size / Short.SIZE_BYTES
    if (sampleCount == 0) return 0f

    var sumOfSquares = 0.0
    var byteIndex = 0
    repeat(sampleCount) {
        val low = bytes[byteIndex].toInt() and 0xff
        val high = bytes[byteIndex + 1].toInt()
        val sample = ((high shl 8) or low).toShort().toInt()
        val normalized = sample / 32768.0
        sumOfSquares += normalized * normalized
        byteIndex += Short.SIZE_BYTES
    }

    val rms = sqrt(sumOfSquares / sampleCount)
    if (rms <= MIN_AUDIBLE_RMS) return 0f
    val decibels = 20.0 * log10(rms)
    return ((decibels - MIN_DECIBELS) / -MIN_DECIBELS).toFloat().coerceIn(0f, 1f)
}

private const val MIN_AUDIBLE_RMS = 0.000_001
private const val MIN_DECIBELS = -60.0
