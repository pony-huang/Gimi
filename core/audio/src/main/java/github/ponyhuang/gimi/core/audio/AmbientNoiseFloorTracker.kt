package github.ponyhuang.gimi.core.audio

/**
 * 环境底噪跟踪器：为识别提交门提供「远处人群声 ≈ 底噪、贴嘴说话 ≫ 底噪」的
 * 电平参照。嘈杂环境的静默间隙帧（低 VAD 概率）的电平即环境底噪，对它做双向
 * 限幅逼近：比例项保证稳态收敛，限幅防单帧尖峰（关门声、咳嗽）立即抬走估计。
 *
 * 两类帧不参与估计：高 VAD 概率的语音帧（否则底噪会被学成语音水平，判据失效），
 * 以及被回声污染的帧（播放期/尾音期，电平含 TTS 残留）。估计封顶 [MAX_FLOOR]，
 * 防止极端持续噪声把「贴嘴说话」也判成不显著于底噪。
 *
 * 逐帧由录音线程喂入；reset 可能由路由切换、静音切换等外部线程触发，故方法加锁。
 */
class AmbientNoiseFloorTracker {
    private var floor = 0f
    private var established = false

    /**
     * @param level 本帧电平（0..1，刻度同 [calculatePcm16Level]）。
     * @param speechProbability 本帧 VAD 概率，必须用回声门控前的原始值——
     *   门控把播放期帧压到 0，会伪装成静默污染估计。
     * @param contaminated 电平是否被回声污染（播放期或播完后的声学尾音期）。
     */
    @Synchronized
    fun accept(level: Float, speechProbability: Float, contaminated: Boolean) {
        if (contaminated || speechProbability >= SPEECH_PROBABILITY_EXCLUSION) return
        val current = if (established) floor else level
        val step = ((level - current) * ADAPT_ALPHA).coerceIn(-MAX_STEP, MAX_STEP)
        floor = (current + step).coerceIn(0f, MAX_FLOOR)
        established = true
    }

    /** 当前底噪估计；null 表示尚未建立（还没有可用的静默帧样本）。 */
    @Synchronized
    fun floor(): Float? = if (established) floor else null

    /** 输入设备重建、路由切换或会话收起时重置：声学路径变化后旧估计不可信。 */
    @Synchronized
    fun reset() {
        established = false
        floor = 0f
    }

    companion object {
        /** VAD 概率达到该值的帧视为语音，不参与底噪估计。 */
        const val SPEECH_PROBABILITY_EXCLUSION = 0.5f

        /** 每帧向目标电平逼近的比例（32 ms/帧）：数秒跟上环境变化，句间停顿不致明显回落。 */
        private const val ADAPT_ALPHA = 0.1f

        /** 单帧最大变化量（0..1 刻度），吸收瞬态尖峰。 */
        private const val MAX_STEP = 0.02f

        /** 底噪估计封顶：极端持续噪声下判据退化为只信画像，不会拒掉全部语音。 */
        const val MAX_FLOOR = 0.5f
    }
}
