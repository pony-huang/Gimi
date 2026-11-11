package github.ponyhuang.gimi.core.audio

import androidx.annotation.Keep

/**
 * 会话独占的软件 AEC：16 kHz 采集以 10 ms 帧处理，参考来自已提交播放的 24 kHz PCM。
 * 时间均为单调时钟；参考提前 20 ms，使设备残余延迟落在 300 ms 自适应滤波窗口内。
 * 静音时也应继续调用以维持滤波器收敛，是否将结果送入 VAD 由调用方决定。
 */
@Keep
class SoftwareEchoCanceller : AutoCloseable {
    private var handle = nativeCreate().also { check(it != 0L) { "Unable to initialize software AEC" } }
    private val microphone = ShortArray(FRAME_SAMPLES)
    private val output = ShortArray(FRAME_SAMPLES)
    // 采集热路径上的每帧分配：clean 缓冲复用而非每次新建，降低通话期间的 GC 压力
    private val clean = ByteArray(FRAME_SAMPLES * 2)
    private var pendingBytes = 0
    private var lowByte = 0
    private var frameTimeNanos = 0L

    /** 跨录音分块保存采样和帧边界；参考查询与输出回调在调用线程执行。 */
    @Synchronized
    fun accept(
        pcm16: ByteArray,
        firstSampleTimeNanos: Long,
        reference: (Long) -> ShortArray,
        onCleanAudio: (ByteArray) -> Unit,
    ) {
        check(handle != 0L)
        pcm16.forEachIndexed { index, byte ->
            if (pendingBytes == 0) frameTimeNanos = firstSampleTimeNanos + index / 2 * SAMPLE_NANOS
            if (pendingBytes % 2 == 0) {
                lowByte = byte.toInt() and 0xff
            } else {
                microphone[pendingBytes / 2] = (lowByte or (byte.toInt() shl 8)).toShort()
            }
            pendingBytes++
            if (pendingBytes == FRAME_SAMPLES * 2) {
                nativeProcess(handle, microphone, reference(frameTimeNanos - REFERENCE_LEAD_NANOS), output)
                output.forEachIndexed { sample, value ->
                    clean[sample * 2] = value.toByte()
                    clean[sample * 2 + 1] = (value.toInt() shr 8).toByte()
                }
                pendingBytes = 0
                // 回调方必须在本帧消费完成前不得跨 accept 调用持有该缓冲
                onCleanAudio(clean)
            }
        }
    }

    /** 路由变化后旧声学路径不再有效，丢弃残帧并重新学习，不关闭麦克风。 */
    @Synchronized
    fun reset() {
        check(handle != 0L)
        nativeDestroy(handle)
        handle = 0
        handle = nativeCreate().also { check(it != 0L) { "Unable to reset software AEC" } }
        pendingBytes = 0
        // 保留 lowByte：若重置恰逢采样字节对中间，丢弃它会让后续字节从奇数偏移
        // 重新配对，所有样本永久高低位错位。
    }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0
        pendingBytes = 0
    }

    private external fun nativeCreate(): Long
    private external fun nativeProcess(handle: Long, microphone: ShortArray, reference: ShortArray, output: ShortArray)
    private external fun nativeDestroy(handle: Long)

    private companion object {
        const val FRAME_SAMPLES = 160
        const val SAMPLE_NANOS = 1_000_000_000L / 16_000
        const val REFERENCE_LEAD_NANOS = 20_000_000L

        init { System.loadLibrary("gimi_echo") }
    }
}
