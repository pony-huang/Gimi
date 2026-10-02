package github.ponyhuang.gimi.data.mobileuse

import kotlin.math.abs

/** 同一次采集的完整截图及采样信息；时间基于 elapsedRealtime，generation 属于截图 Surface。 */
internal class CapturedMobileFrame(
    val width: Int,
    val height: Int,
    val capturedAtMs: Long,
    val sequence: Long,
    val generation: Long,
    val jpeg: ByteArray,
    val samples: IntArray,
)

/** 用局部像素变化和目标屏幕事件判断画面是否静止，不推断页面业务是否加载完成。 */
internal class MobileFrameStability(private val startedAtMs: Long, private val afterActionMs: Long? = null) {
    private var anchor: CapturedMobileFrame? = null
    private var quietSinceMs = startedAtMs
    private var reason = "no_frame"

    fun update(frame: CapturedMobileFrame?, nowMs: Long, lastEventMs: Long): Boolean {
        if (frame == null) {
            reason = "no_frame"
            return false
        }
        val previous = anchor
        if (previous == null || !samePicture(previous, frame)) {
            anchor = frame
            quietSinceMs = nowMs
        }
        quietSinceMs = maxOf(quietSinceMs, lastEventMs)
        reason = when {
            suspectedBlank(frame.samples) -> "suspected_blank"
            afterActionMs != null && frame.capturedAtMs <= afterActionMs -> "no_post_action_frame"
            nowMs - quietSinceMs < 500 -> "changing"
            else -> "quiet"
        }
        return reason == "quiet"
    }

    fun timeoutReason(): String = reason

    companion object {
        private const val SAMPLE_WIDTH = 64
        private const val SAMPLE_HEIGHT = 96

        fun samePicture(first: CapturedMobileFrame, second: CapturedMobileFrame): Boolean {
            if (first.width != second.width || first.height != second.height ||
                first.generation != second.generation || first.samples.size != SAMPLE_WIDTH * SAMPLE_HEIGHT ||
                second.samples.size != first.samples.size
            ) return false
            // 小区域单独比较，避免购物车数量等局部变化被全屏平均或感知哈希掩盖。
            for (tileY in 0 until SAMPLE_HEIGHT step 8) {
                for (tileX in 0 until SAMPLE_WIDTH step 8) {
                    var difference = 0
                    for (y in tileY until tileY + 8) {
                        for (x in tileX until tileX + 8) {
                            val index = y * SAMPLE_WIDTH + x
                            difference += abs(first.samples[index] - second.samples[index])
                        }
                    }
                    if (difference > 64 * 3) return false
                }
            }
            return true
        }

        private fun suspectedBlank(samples: IntArray): Boolean {
            if (samples.isEmpty()) return true
            val average = samples.average()
            val variance = samples.sumOf { (it - average) * (it - average) } / samples.size
            return (average > 247 || average < 8) && variance < 12
        }
    }
}
