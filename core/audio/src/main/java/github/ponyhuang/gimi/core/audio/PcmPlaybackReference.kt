package github.ponyhuang.gimi.core.audio

import kotlin.math.floor

/**
 * 单条 24 kHz AudioTrack 的有界 PCM 历史，以平台播放时钟定位参考，禁止把未来排队音频当成回声。
 * 调用方串行访问；停止后截断未播放数据，但保留已播放历史供房间回声尾音使用。
 */
class PcmPlaybackReference {
    // 环形缓冲容量 = 采样率 × 2 字节 ≈ 2 秒的 24 kHz PCM 历史，覆盖房间回声尾音
    private val samples = ShortArray(VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ * VoiceAudioFormat.BYTES_PER_SAMPLE)
    private var written = 0L
    private var endFrame = Long.MAX_VALUE
    private var anchorFrame = 0L
    private var anchorNanos: Long? = null

    fun append(pcm16: ByteArray, offset: Int, size: Int) {
        require(offset >= 0 && size >= 0 && size % 2 == 0 && offset + size <= pcm16.size)
        for (index in offset until offset + size step 2) {
            samples[(written % samples.size).toInt()] =
                ((pcm16[index].toInt() and 0xff) or (pcm16[index + 1].toInt() shl 8)).toShort()
            written++
        }
    }

    fun anchor(frame: Long, timeNanos: Long) {
        anchorFrame = frame
        anchorNanos = timeNanos
    }

    fun stop(playedFrames: Long) { endFrame = playedFrames.coerceAtMost(written) }

    /** 累加实际时间区间内的参考；两个句子间的静默保持为零。 */
    fun mixInto(firstSampleTimeNanos: Long, destination: ShortArray) {
        val time = anchorNanos ?: return
        val first = anchorFrame + floor((firstSampleTimeNanos - time) * VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ / 1_000_000_000.0).toLong()
        destination.indices.forEach { index ->
            val frame = first + index
            if (frame >= maxOf(0, written - samples.size) && frame < minOf(written, endFrame)) {
                destination[index] = (destination[index] + samples[(frame % samples.size).toInt()].toInt())
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
