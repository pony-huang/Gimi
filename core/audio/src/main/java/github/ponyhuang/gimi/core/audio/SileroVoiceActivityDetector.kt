package github.ponyhuang.gimi.core.audio

import android.content.res.AssetManager
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * 本地 Silero VAD v6.2，逐帧处理 16 kHz 单声道 PCM16。
 * 每个录音流独占隐藏状态和前文采样；不与其他会话共享模型状态。
 * 模型来源：https://github.com/snakers4/silero-vad/tree/v6.2（随资源附 MIT 许可）。
 */
class SileroVoiceActivityDetector(assets: AssetManager) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        val model = assets.open("silero/silero_vad_v6_2.onnx").use { it.readBytes() }
        environment.createSession(model, options)
    }
    private val frame = ByteArray(VoiceActivitySegmenter.FRAME_BYTES)
    private val context = FloatArray(CONTEXT_SAMPLES)
    private val hiddenState = FloatArray(HIDDEN_SAMPLES)
    // 32 ms 帧的热路径：仅复用输入数组；张量必须每帧重建——堆内 Buffer 在
    // createTensor 时拷贝进原生内存，之后写回 Java 数组不会进入张量，
    // 复用会让 VAD 输入恒为构造时的零值（表现为永远检测不到说话）。
    private val samples = FloatArray(VoiceActivitySegmenter.FRAME_BYTES / 2 + CONTEXT_SAMPLES)
    private var frameOffset = 0
    private var closed = false

    /** 录音块可以包含任意数量的字节；回调始终收到完整的 512 采样帧。 */
    @Synchronized
    fun accept(pcm16: ByteArray, onFrame: (ByteArray, Float) -> Unit) {
        check(!closed)
        var offset = 0
        while (offset < pcm16.size) {
            val size = minOf(frame.size - frameOffset, pcm16.size - offset)
            pcm16.copyInto(frame, frameOffset, offset, offset + size)
            offset += size
            frameOffset += size
            if (frameOffset == frame.size) {
                frameOffset = 0
                // 回调方不得跨帧持有该缓冲（当前消费方 segmenter 会立即拷贝）
                onFrame(frame, probability())
            }
        }
    }

    private fun probability(): Float {
        context.copyInto(samples)
        repeat(VoiceActivitySegmenter.FRAME_BYTES / 2) { index ->
            val low = frame[index * 2].toInt() and 255
            val high = frame[index * 2 + 1].toInt()
            samples[index + CONTEXT_SAMPLES] = ((high shl 8) or low).toShort() / 32768f
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { input ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(hiddenState), longArrayOf(2, 1, 128)).use { state ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(SAMPLE_RATE_HZ)), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to input, "state" to state, "sr" to rate)).use { result ->
                        val probability = (result.get("output").get() as OnnxTensor).floatBuffer.get(0)
                        (result.get("stateN").get() as OnnxTensor).floatBuffer.get(hiddenState)
                        samples.copyInto(context, startIndex = samples.size - context.size)
                        return probability.coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    /** 静音和设备重建时重置所有时间状态，防止上一段人声污染新的输入。 */
    @Synchronized
    fun reset() {
        check(!closed)
        frameOffset = 0
        frame.fill(0)
        context.fill(0f)
        hiddenState.fill(0f)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            session.close()
        }
    }

    private companion object {
        const val CONTEXT_SAMPLES = 64
        const val HIDDEN_SAMPLES = 256
        const val SAMPLE_RATE_HZ = 16_000L
    }
}
