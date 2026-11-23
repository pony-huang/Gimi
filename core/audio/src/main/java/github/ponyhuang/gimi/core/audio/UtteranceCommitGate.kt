package github.ponyhuang.gimi.core.audio

/**
 * 识别提交门：切句器交出完整语句后、送识别前的一次性段级判定。
 *
 * 起始确认（连续 ≥0.75 概率）只能挡瞬态噪声；持续人群说话声对 Silero 而言
 * 也是语音，会正常开轮并产出整段音频。此门用段画像与「电平-底噪」对比再拦
 * 一道：边缘段（咳嗽尾音、短促杂音、远处人群声）不达标直接丢弃，不花识别
 * 成本、不产生幻觉回答。丢弃语义与空识别一致：无 generation、无 context
 * slot、直接回监听。
 *
 * 边界：画像与电平判据挡不住「电平显著高于底噪的硬 babble 段」——远端语音
 * 保留型手段对其原理性无效，不是参数问题；门槛值均为起步值，待真机标定。
 */
object UtteranceCommitGate {
    /**
     * @param segment 切句器输出的语句画像与音频。
     * @param noiseFloor 环境底噪估计（0..1，同电平刻度）；null 表示尚未建立
     *   （会话刚开始、尚无静默帧样本），此时退化为纯画像判定，电平判据跳过。
     */
    fun allows(segment: UtteranceSegment, noiseFloor: Float?): Boolean {
        if (segment.frameCount == 0 || segment.voicedFrameCount == 0) return false
        if (segment.peakProbability < PEAK_PROBABILITY_MINIMUM) return false
        if (segment.speechFrameCount < MIN_SPEECH_FRAMES) return false
        // 占比只对非静默帧计算:句末 704ms 静默尾与 pre-roll 静音是切句器的固定产物,
        // 计入分母会系统性拒绝短语句(hi/hello/你好 的浊音段远短于静默尾)。
        if (segment.speechFrameCount.toFloat() / segment.voicedFrameCount < SPEECH_FRAME_RATIO_MINIMUM) return false
        val floor = noiseFloor ?: return true
        return segment.peakLevel >= floor + NOISE_LEVEL_MARGIN
    }

    /** 段内最高概率下限：清晰近端语音通常到顶，瞬态与远处人群声很难达到。 */
    const val PEAK_PROBABILITY_MINIMUM = 0.9f

    /** 有效语音帧数下限（32 ms/帧）：6 帧约 192 ms,短问候(hi/你好)可过,单帧瞬态尖峰被拒。 */
    const val MIN_SPEECH_FRAMES = 6

    /** 有效语音帧占非静默帧的比例下限：语音段的大部分时间应处于高置信状态。 */
    const val SPEECH_FRAME_RATIO_MINIMUM = 0.5f

    /** 峰值电平超出底噪的最小幅度（0..1 刻度，约 7 dB），与打断判定的电平余量同口径。 */
    const val NOISE_LEVEL_MARGIN = 0.12f
}
