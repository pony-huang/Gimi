package github.ponyhuang.gimi.core.audio

/**
 * 单声道 PCM16 线性插值升采样器（如 16 kHz 录音上行到 24 kHz 的实时会话）。
 *
 * 只支持升采样（[outputRateHz] > [inputRateHz]）：步进小于一个输入样本时，
 * 插值区间永远只涉及「上一 chunk 的末样本 + 当前样本」，因此流式分块调用与
 * 一次性整段调用产出逐字节一致，无需在调用方聚合整段音频。
 *
 * 全程用整数有理数累加（`输出序号 × 输入率 / 输出率`），不用浮点相位，
 * 避免长会话（Realtime 上限 60 分钟）里的相位漂移。
 *
 * 字节序为小端 PCM16；输入块必须是完整采样（偶数字节），输出同理。
 */
class Pcm16Resampler(
    private val inputRateHz: Int,
    private val outputRateHz: Int,
) {
    init {
        require(outputRateHz > inputRateHz) { "Pcm16Resampler 只支持升采样" }
        require(inputRateHz > 0 && outputRateHz > 0) { "采样率必须为正" }
    }

    /** 已消费的输入样本总数（含跨块累计）。 */
    private var consumedSamples = 0L

    /** 已产出的输出样本总数。 */
    private var producedSamples = 0L

    /** 上一块的最后一个输入样本；跨块插值的左端点。 */
    private var previousSample = 0

    /**
     * 送入一段输入音频，返回对应的输出音频。
     *
     * 空块返回空数组；块边界任意（只要偶数字节对齐），不影响输出序列。
     */
    fun process(input: ByteArray): ByteArray {
        require(input.size % 2 == 0) { "PCM16 输入必须是完整采样（偶数字节）" }
        if (input.isEmpty()) return ByteArray(0)
        val out = ByteArray((estimateOutputCount(input.size / 2) * 2L).toInt())
        var outPos = 0
        var index = 0
        while (index + 1 < input.size) {
            val sample = ((input[index + 1].toInt() and 0xFF) shl 8) or (input[index].toInt() and 0xFF)
            index += 2
            consumedSamples++
            if (consumedSamples == 1L) {
                // 第一个样本还没有左端点；它只作为后续区间的插值起点。
                previousSample = sample
                continue
            }
            // 区间 [consumed-2, consumed-1] 上的输出：x = 输出序号 × 输入率 / 输出率。
            while (true) {
                val xNumerator = producedSamples * inputRateHz
                val floor = xNumerator / outputRateHz
                // 插值需要左右两个端点：右端点 floor+1 必须已消费，即 floor <= consumed-2。
                // 升采样步进小于 1，floor 落到 consumed-1 时该输出推迟到下一块/flush 以末样本为左端点补出。
                if (floor > consumedSamples - 2) break
                val fractionNumerator = xNumerator % outputRateHz
                val interpolated = previousSample + ((sample - previousSample) * fractionNumerator / outputRateHz)
                out[outPos++] = (interpolated and 0xFF).toByte()
                out[outPos++] = ((interpolated shr 8) and 0xFF).toByte()
                producedSamples++
            }
            previousSample = sample
        }
        return out.copyOf(outPos)
    }

    /**
     * 输入结束。剩余输出位置插值区间缺少右端点，按「保持最后样本」补齐，
     * 使输出总量与整段重采样一致（末尾最多保持不足一个输入样本的时长）。
     */
    fun flush(): ByteArray {
        // 剩余输出满足 floor <= consumed-1；区间左右端都是最后样本，输出恒等于它。
        val remaining = targetCount(consumedSamples) - producedSamples
        if (remaining <= 0) return ByteArray(0)
        val out = ByteArray((remaining * 2).toInt())
        var outPos = 0
        repeat(remaining.toInt()) {
            out[outPos++] = (previousSample and 0xFF).toByte()
            out[outPos++] = ((previousSample shr 8) and 0xFF).toByte()
        }
        producedSamples += remaining
        return out
    }

    /** 输入 [inputSamples] 个样本后理论上的输出总量（向上取整的有理数目标）。 */
    private fun targetCount(inputSamples: Long): Long =
        (inputSamples * outputRateHz + inputRateHz - 1) / inputRateHz

    /** 单块产出的上界估计，只用于预分配缓冲。 */
    private fun estimateOutputCount(inputSamples: Int): Long =
        inputSamples.toLong() * outputRateHz / inputRateHz + 2
}
