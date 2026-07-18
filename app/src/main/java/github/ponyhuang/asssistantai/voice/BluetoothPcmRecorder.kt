package github.ponyhuang.asssistantai.voice

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
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
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onError(IllegalStateException("无法确定蓝牙录音缓冲区大小"))
            return false
        }
        val audioRecord = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
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
            onError(IllegalStateException("无法将麦克风路由到蓝牙耳机"))
            return false
        }
        return runCatching {
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "蓝牙麦克风没有开始录音"
            }
            recorder = audioRecord
            readJob = scope.launch {
                val buffer = ByteArray(FRAME_BYTES)
                while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when {
                        count > 0 -> onChunk(buffer.copyOf(count))
                        count < 0 && !stopping.get() -> {
                            onError(IllegalStateException("蓝牙麦克风读取失败：$count"))
                            break
                        }
                    }
                }
            }
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

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val FRAME_BYTES = 3_200
    }
}
