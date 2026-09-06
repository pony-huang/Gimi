package github.ponyhuang.gimi.data.voicewake

import kotlin.math.sqrt

/**
 * 唤醒等待期的静音能量门控。
 *
 * 常驻唤醒场景下绝大多数音频块是纯静音，逐块跑 Vosk 解码是持续 CPU 功耗的主要来源；
 * 本门控按块计算 16-bit PCM 的 RMS，低于阈值的静音块跳过解码，只在检测到声音后
 * 的短暂拖尾窗口内继续喂入（保证词尾与识别端点处理不丢）。
 * 门控只影响唤醒识别器消费，预滚缓冲与命令采集不受影响。
 *
 * 阈值取保守值（约 -42dBFS）：嘈杂环境退化为逐块解码（不漏检），安静环境显著省电。
 */
internal class WakeEnergyGate {
    private var hangoverRemaining = 0

    /** 返回该音频块是否需要送入唤醒识别器。 */
    fun shouldFeed(chunk: ByteArray): Boolean {
        val rms = computeRms(chunk)
        return if (rms >= SPEECH_RMS_THRESHOLD) {
            hangoverRemaining = HANGOVER_CHUNKS
            true
        } else {
            val feed = hangoverRemaining > 0
            if (feed) hangoverRemaining -= 1
            feed
        }
    }

    /** 丢弃拖尾状态；停止采集或重建监听时调用，避免跨会话残留。 */
    fun reset() {
        hangoverRemaining = 0
    }

    private fun computeRms(chunk: ByteArray): Int {
        if (chunk.size < 2) return 0
        var sumSquares = 0L
        var sampleCount = 0
        var index = 0
        while (index + 1 < chunk.size) {
            // PCM 16-bit 小端，先合成无符号再转 Short 恢复符号位。
            val sample = ((chunk[index + 1].toInt() shl 8) or (chunk[index].toInt() and 0xFF))
                .toShort().toInt()
            sumSquares += sample.toLong() * sample
            sampleCount++
            index += 2
        }
        return if (sampleCount == 0) 0 else sqrt(sumSquares.toDouble() / sampleCount).toInt()
    }

    private companion object {
        const val SPEECH_RMS_THRESHOLD = 250

        /** 声音结束后继续喂入的块数；100ms/块对应 2.5s 拖尾。 */
        const val HANGOVER_CHUNKS = 25
    }
}
