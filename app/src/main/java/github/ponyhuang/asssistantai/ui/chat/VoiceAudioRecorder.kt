package github.ponyhuang.asssistantai.ui.chat

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Captures microphone input as 16 kHz, mono, signed 16-bit PCM chunks. */
internal class VoiceAudioRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var readJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    fun start(onAudioChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean {
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
                while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when {
                        bytesRead > 0 -> onAudioChunk(buffer.copyOf(bytesRead))
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
    }
}
