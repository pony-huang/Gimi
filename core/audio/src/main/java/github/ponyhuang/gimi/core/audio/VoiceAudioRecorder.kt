package github.ponyhuang.gimi.core.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val captureEffects = mutableListOf<AudioEffect>()
    // start 写入、读循环 finally 在 IO 线程复位：外部读取需保证可见性
    @Volatile
    var echoCancellationEnabled: Boolean = false
        private set

    @Volatile
    var noiseSuppressionEnabled: Boolean = false
        private set

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    fun start(
        onAudioChunk: (ByteArray) -> Unit,
        onAudioLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit,
        voiceCommunication: Boolean = false,
        onTimedAudioChunk: ((ByteArray, Long) -> Unit)? = null,
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
                .setAudioSource(if (voiceCommunication) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION)
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
            // 实时会话显式绑定录音 session 的处理器，避免影响普通听写和其他播放会话。
            if (voiceCommunication) {
                // 处理器为设备可选能力：耳机或厂商实现不支持时仍允许通话。
                echoCancellationEnabled = enableCaptureEffect(AcousticEchoCanceler::isAvailable) {
                    AcousticEchoCanceler.create(recorder.audioSessionId)
                }
                noiseSuppressionEnabled = enableCaptureEffect(NoiseSuppressor::isAvailable) {
                    NoiseSuppressor.create(recorder.audioSessionId)
                }
            }
            recorder.startRecording()
            audioRecord = recorder
            readJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    cancellationAwareRunCatching {
                        val buffer = ByteArray(bufferSize)
                        var lastLevelDispatchMs = 0L
                        var smoothedLevel = 0f
                        var readSamples = 0L
                        val timestamp = AudioTimestamp()
                        while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val bytesRead = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                            when {
                                bytesRead > 0 -> {
                                    val chunk = buffer.copyOf(bytesRead)
                                    val firstSample = readSamples
                                    readSamples += bytesRead / 2
                                    if (onTimedAudioChunk != null) {
                                        // 采集时间不能用回调到达时间替代，否则录音缓冲延迟会使 AEC 参考错位。
                                        val firstTime = if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                                            timestamp.nanoTime + (firstSample - timestamp.framePosition) * 1_000_000_000L / SAMPLE_RATE_HZ
                                        } else {
                                            System.nanoTime() - bytesRead / 2 * 1_000_000_000L / SAMPLE_RATE_HZ
                                        }
                                        onTimedAudioChunk(chunk, firstTime)
                                    } else onAudioChunk(chunk)
                                    val measuredLevel = calculatePcm16Level(chunk)
                                    smoothedLevel = (smoothedLevel * LEVEL_SMOOTHING) +
                                        (measuredLevel * (1f - LEVEL_SMOOTHING))
                                    val nowMs = SystemClock.elapsedRealtime()
                                    if (nowMs - lastLevelDispatchMs >= LEVEL_DISPATCH_INTERVAL_MS) {
                                        lastLevelDispatchMs = nowMs
                                        onAudioLevel(smoothedLevel)
                                    }
                                }
                                bytesRead < 0 ->
                                    throw IllegalStateException("Microphone read failed: $bytesRead")
                            }
                        }
                    }.onFailure { error ->
                        runCatching { onError(error) }
                    }
                } finally {
                    synchronized(this@VoiceAudioRecorder) {
                        if (audioRecord === recorder) {
                            audioRecord = null
                            readJob = null
                            releaseCaptureEffects()
                            runCatching {
                                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                    recorder.stop()
                                }
                                recorder.release()
                            }
                        }
                    }
                }
            }
            readJob?.start()
            true
        } catch (error: Throwable) {
            releaseCaptureEffects()
            recorder.release()
            onError(error)
            false
        }
    }

    private fun enableCaptureEffect(available: () -> Boolean, create: () -> AudioEffect?): Boolean {
        return try {
            if (!available()) return false
            val effect = create() ?: return false
            captureEffects.add(effect)
            effect.setEnabled(true) == AudioEffect.SUCCESS && effect.enabled
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    private fun releaseCaptureEffects() {
        captureEffects.forEach { effect -> runCatching { effect.release() } }
        captureEffects.clear()
        echoCancellationEnabled = false
        noiseSuppressionEnabled = false
    }

    @Synchronized
    fun stop() {
        // 取消无法打断阻塞中的原生 read()，在 IO 线程仍停在 read 时 release()
        // 是设备相关的崩溃/未定义行为路径。这里只 cancel 并 stop()（stop() 会让
        // 阻塞 read 立即返回），stop()/release() 与字段清空统一由读循环的
        // finally 在同一把锁内完成。
        readJob?.cancel()
        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { recorder.stop() }
            }
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /** 等待原生读取退出和设备释放，供实时会话失败重试时安全重建录音流。 */
    suspend fun stopAndJoin() {
        val reading = synchronized(this) { readJob }
        stop()
        reading?.join()
    }

    /** 退出实时会话时等待设备释放，防止下一会话与旧录音争用音频资源。 */
    suspend fun releaseAndJoin() {
        stopAndJoin()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE_HZ = VoiceAudioFormat.INPUT_SAMPLE_RATE_HZ
        private const val LEVEL_DISPATCH_INTERVAL_MS = 50L
        private const val LEVEL_SMOOTHING = 0.65f
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

    return levelFromRms(sqrt(sumOfSquares / sampleCount))
}

/**
 * 播放参考信号（24 kHz PCM16 采样数组）的电平，刻度与 [calculatePcm16Level] 一致，
 * 可直接与麦克风电平比较：两者都由 −60..0 dBFS 线性映射到 0..1。
 */
fun calculatePcm16Level(samples: ShortArray): Float {
    if (samples.isEmpty()) return 0f
    var sumOfSquares = 0.0
    for (sample in samples) {
        val normalized = sample / 32768.0
        sumOfSquares += normalized * normalized
    }
    return levelFromRms(sqrt(sumOfSquares / samples.size))
}

private fun levelFromRms(rms: Double): Float {
    if (rms <= MIN_AUDIBLE_RMS) return 0f
    val decibels = 20.0 * log10(rms)
    return ((decibels - MIN_DECIBELS) / -MIN_DECIBELS).toFloat().coerceIn(0f, 1f)
}

private const val MIN_AUDIBLE_RMS = 0.000_001
private const val MIN_DECIBELS = -60.0
