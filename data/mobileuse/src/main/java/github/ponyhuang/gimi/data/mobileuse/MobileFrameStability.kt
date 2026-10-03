package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
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

        /** 仅复核点按目标周围的采样；其他区域动画不会使固定目标失效。 */
        fun sameRegion(first: CapturedMobileFrame, second: CapturedMobileFrame, bounds: MobileBounds): Boolean {
            if (first.width <= 0 || first.height <= 0 || bounds.left >= bounds.right || bounds.top >= bounds.bottom) return false
            if (first.width != second.width || first.height != second.height || first.generation != second.generation) return false
            if (bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= first.width || bounds.top >= first.height) return false
            if (first.samples.size != SAMPLE_WIDTH * SAMPLE_HEIGHT || second.samples.size != first.samples.size) {
                return first.jpeg.contentEquals(second.jpeg)
            }
            val left = (bounds.left.coerceAtLeast(0).toLong() * SAMPLE_WIDTH / first.width).toInt()
            val right = ((bounds.right.coerceAtMost(first.width).toLong() * SAMPLE_WIDTH + first.width - 1) / first.width).toInt()
            val top = (bounds.top.coerceAtLeast(0).toLong() * SAMPLE_HEIGHT / first.height).toInt()
            val bottom = ((bounds.bottom.coerceAtMost(first.height).toLong() * SAMPLE_HEIGHT + first.height - 1) / first.height).toInt()
            for (y in top until bottom) {
                for (x in left until right) {
                    if (abs(first.samples[y * SAMPLE_WIDTH + x] - second.samples[y * SAMPLE_WIDTH + x]) > 3) return false
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
