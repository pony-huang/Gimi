package github.ponyhuang.gimi.data.voice.playback

import github.ponyhuang.gimi.core.audio.VoiceAudioFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTimestamp
import github.ponyhuang.gimi.core.audio.PcmPlaybackReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** 实时会话独占的 PCM 播放器；不复用文字聊天的全局播放状态或音频缓存。 */
internal class RealtimePcmPlayer {
    private val lock = Any()
    private var generation = 0L
    private var activeTrack: AudioTrack? = null
    private var activeReference: PcmPlaybackReference? = null
    private val references = ArrayDeque<PcmPlaybackReference>()
    private val timestamp = AudioTimestamp()

    /** 由采集线程按麦克风时间查询；使用硬件播放时钟，不使用 TTS 到包时间。 */
    fun echoReference(firstSampleTimeNanos: Long): ShortArray = synchronized(lock) {
        updateReferenceClock()
        ShortArray(240).also { output -> references.forEach { it.mixInto(firstSampleTimeNanos, output) } }
    }

    private fun updateReferenceClock() {
        val track = activeTrack ?: return
        val reference = activeReference ?: return
        if (track.getTimestamp(timestamp)) {
            reference.anchor(timestamp.framePosition, timestamp.nanoTime)
        } else {
            reference.anchor(track.playbackHeadPosition.toLong() and 0xffff_ffffL, System.nanoTime())
        }
    }

    /** 返回时表示该段音频已实际播完，可将对应句子加入已听上下文。 */
    fun checkpoint(): Long = synchronized(lock) { generation }

    /** 是否正在向硬件写入音频；回声保护窗口据此判断残留回声是否会进 VAD。 */
    val playing: Boolean get() = synchronized(lock) { activeTrack != null }

    suspend fun play(source: Flow<ByteArray>, token: Long, onLevel: (Float) -> Unit = {}, onStarted: () -> Unit) {
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minimum = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "Unsupported voice playback format" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimum, 4_800))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            synchronized(lock) {
                checkGeneration(token)
                check(activeTrack == null) { "Voice playback must be sequential" }
                activeTrack = track
                activeReference = PcmPlaybackReference().also {
                    references.addLast(it)
                    // 保留上一句尾音，内存上限固定，不积累整场通话。
                    while (references.size > 2) references.removeFirst()
                }
                track.play()
            }
            var writtenBytes = 0L
            var started = false
            var trailingByte: Byte? = null
            source.collect { chunk ->
                // 网络分块不保证采样边界；跨块保留最后一个字节，而非丢弃或误报失败。
                val bytes = trailingByte?.let { byteArrayOf(it) + chunk } ?: chunk
                val playableSize = bytes.size - bytes.size % 2
                trailingByte = if (playableSize < bytes.size) bytes.last() else null
                var offset = 0
                while (offset < playableSize) {
                    currentCoroutineContext().ensureActive()
                    val count = synchronized(lock) {
                        checkGeneration(token)
                        // 非阻塞写入使停播不依赖原生 write 返回，取消后不会继续灌入旧音频。
                        track.write(bytes, offset, playableSize - offset, AudioTrack.WRITE_NON_BLOCKING).also { count ->
                            if (count > 0) activeReference?.append(bytes, offset, count)
                        }
                    }
                    check(count >= 0) { "Voice playback write failed: $count" }
                    if (count == 0) {
                        delay(5)
                    } else {
                        if (!started) {
                            started = true
                            onStarted()
                        }
                        var energy = 0.0
                        for (index in offset until offset + count - 1 step 2) {
                            val sample = ((bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)).toShort() / 32768.0
                            energy += sample * sample
                        }
                        onLevel((kotlin.math.sqrt(energy / (count / 2).coerceAtLeast(1)) * 4).toFloat().coerceIn(0f, 1f))
                        offset += count
                        writtenBytes += count
                    }
                }
            }
            check(trailingByte == null) { "Speech synthesis returned incomplete PCM16 audio" }
            check(writtenBytes > 0) { "Speech synthesis returned no audio" }
            while (true) {
                currentCoroutineContext().ensureActive()
                val drained = synchronized(lock) {
                    checkGeneration(token)
                    (track.playbackHeadPosition.toLong() and 0xffff_ffffL) >= writtenBytes / 2
                }
                if (drained) break
                delay(10)
            }
        } finally {
            onLevel(0f)
            synchronized(lock) {
                if (activeTrack === track) {
                    updateReferenceClock()
                    activeReference?.stop(track.playbackHeadPosition.toLong() and 0xffff_ffffL)
                    activeTrack = null
                    activeReference = null
                }
                track.release()
            }
        }
    }

    /** 立即暂停并清空硬件队列；资源由持有该 track 的播放协程统一释放。 */
    fun stop() {
        synchronized(lock) {
            generation++
            activeTrack?.let { track ->
                try {
                    updateReferenceClock()
                    activeReference?.stop(track.playbackHeadPosition.toLong() and 0xffff_ffffL)
                    activeReference = null
                    track.pause()
                    track.flush()
                } catch (_: IllegalStateException) {
                    // 输出设备失效时仍须使旧代次失效；持有者将在 finally 释放 track。
                }
            }
        }
    }

    private fun checkGeneration(token: Long) {
        if (generation != token) throw CancellationException("Voice playback interrupted")
    }

    private companion object {
        const val SAMPLE_RATE = VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ
    }
}
