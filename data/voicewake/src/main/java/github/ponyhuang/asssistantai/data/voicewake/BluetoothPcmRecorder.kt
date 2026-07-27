package github.ponyhuang.asssistantai.data.voicewake

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import github.ponyhuang.asssistantai.core.audio.VoiceAudioRecorder
import github.ponyhuang.asssistantai.core.common.concurrent.cancellationAwareRunCatching
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothPcmRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var readJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    fun start(
        route: BluetoothAudioRoute,
        onChunk: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (recorder != null) return false
        stopping.set(false)
        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceAudioRecorder.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onError(BluetoothRecorderException(BluetoothRecorderException.Reason.BufferSizeUnavailable))
            return false
        }
        val audioRecord = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(VoiceAudioRecorder.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer * 2, FRAME_BYTES * 2))
                .build()
        }.getOrElse {
            onError(it)
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED || !audioRecord.setPreferredDevice(route.input)) {
            audioRecord.release()
            onError(BluetoothRecorderException(BluetoothRecorderException.Reason.MicrophoneRouteFailed))
            return false
        }
        return runCatching {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw BluetoothRecorderException(BluetoothRecorderException.Reason.NotRecording)
            }
            recorder = audioRecord
            readJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    cancellationAwareRunCatching {
                        val buffer = ByteArray(FRAME_BYTES)
                        while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                            when {
                                count > 0 -> onChunk(buffer.copyOf(count))
                                count < 0 && !stopping.get() -> throw BluetoothRecorderException(
                                    BluetoothRecorderException.Reason.ReadFailed,
                                    errorCode = count,
                                )
                            }
                        }
                    }.onFailure { error ->
                        runCatching { onError(error) }
                    }
                } finally {
                    synchronized(this@BluetoothPcmRecorder) {
                        if (recorder === audioRecord) {
                            recorder = null
                            readJob = null
                            runCatching {
                                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                    audioRecord.stop()
                                }
                                audioRecord.release()
                            }
                        }
                    }
                }
            }
            readJob?.start()
            true
        }.getOrElse {
            audioRecord.release()
            onError(it)
            false
        }
    }

    @Synchronized
    fun stop() {
        stopping.set(true)
        readJob?.cancel()
        readJob = null
        recorder?.runCatching {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            release()
        }
        recorder = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private companion object {
        const val FRAME_BYTES = 3_200
    }
}

class BluetoothRecorderException(
    val reason: Reason,
    val errorCode: Int? = null,
) : IllegalStateException(reason.name) {
    enum class Reason { BufferSizeUnavailable, MicrophoneRouteFailed, NotRecording, ReadFailed }
}
