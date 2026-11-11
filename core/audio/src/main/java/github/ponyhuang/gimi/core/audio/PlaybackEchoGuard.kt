package github.ponyhuang.gimi.core.audio

/**
 * 播放期回声门控：会话全程常驻，录音线程每一帧都过这里决定能否以原始 VAD
 * 概率进入切句器与上行通道。
 *
 * 返回 0f 表示「这一帧不可信，不能当作用户开口」，但仍以该概率喂入切句器，
 * 于是前置音频照常留在 pre-roll 里、VAD 隐藏状态照常推进，只是不会触发
 * speechStarted，也不会打开播放期的上行闸门。
 *
 * 判断不依赖 AEC 是否真的把回声消干净：硬件 AEC 可能声明可用却不生效，
 * 软件 AEC 也可能在播放中途失收敛，此门控是最后一道保险。两种语音模式
 * （全双工 Realtime 与 ASR+LLM+TTS 级联）共用同一份决策。
 *
 * 「常驻」指决策常驻，不是抑制常驻：播放期及播完后的声学尾音期每帧都要判定，
 * 但明显强于回声的近端人声仍会放行，否则播报期间无法用语音打断，等于把全双工
 * 降级成半双工。只有真正静置之后才恢复零延迟直通。
 *
 * 主开关取播放状态（本地可靠），不取播放参考电平：参考时钟锚点漂移会让
 * echoReference 虚假返回静音，把它当开关等于在播报最响时撤防。回声基线取自
 * 麦克风实测电平，不依赖播放时钟，才是可靠的比较基准。
 */
class PlaybackEchoGuard(
    private val clock: () -> Long = System::nanoTime,
) {
    // onAecReset 由主线程（会话启动、路由回调）写，gate 由录音线程读；其余状态
    // 只在录音线程内串行访问，无需额外同步。
    @Volatile
    private var pendingConvergence = false
    private var convergeUntilNanos = 0L
    private var wasPlaying = false
    private var playbackEndedAtNanos = 0L
    private var echoFloor: Float? = null
    private var nearEndStreak = 0
    private var nearEndLatched = false

    /** AEC 从零收敛（会话建立、路由切换）；窗口待到下一次真正播放时起算。 */
    fun onAecReset() {
        pendingConvergence = true
    }

    /**
     * 上行门控复用同一风险窗：播放刚结束时服务端 server_vad 一样会收到混响尾音，
     * 若此刻恢复全量上行，判轮与本地切句器一样会误触发新一轮回答。
     */
    fun tailRisk(): Boolean = playbackEndedAtNanos > 0L &&
        clock() - playbackEndedAtNanos < PLAYBACK_TAIL_NANOS

    /**
     * @param microphoneLevel 本帧麦克风音频的电平，刻度同 [calculatePcm16Level]。
     * @param referenceLevel 同一时刻的播放参考电平；仅作近端判据，不作门控开关。
     */
    fun gate(
        isPlaying: Boolean,
        speechProbability: Float,
        microphoneLevel: Float,
        referenceLevel: Float,
    ): Float {
        val now = clock()
        if (!isPlaying && wasPlaying) {
            playbackEndedAtNanos = now
            wasPlaying = false
        }
        // 播完不等于回声消失：AudioTrack 排空后仍有房间混响、扬声器余振和 AEC
        // 滤波器残留。这段尾音期必须继续过门控，否则「播着播着突然录到声音」
        // 恰好落在 playing 变 false 的那一瞬间。
        if (!isPlaying && !tailRisk()) {
            nearEndStreak = 0
            nearEndLatched = false
            // 句间短间隙保留回声基线，级联模式逐句播放才不会反复重建；
            // 只有真正静置一段时间后才丢弃。
            if (now - playbackEndedAtNanos >= PLAYBACK_IDLE_NANOS) echoFloor = null
            return speechProbability
        }
        if (isPlaying && !wasPlaying) {
            wasPlaying = true
            nearEndStreak = 0
            nearEndLatched = false
            if (pendingConvergence) {
                // 重置时刻不开始计时：会话启动 → 识别 → 生成 → 首个 TTS 包这段延迟
                // 会把按重置时刻起算的窗口全部烧完。
                convergeUntilNanos = now + CONVERGE_WINDOW_NANOS
                pendingConvergence = false
            }
        }
        updateEchoFloor(microphoneLevel)
        if (now < convergeUntilNanos) {
            nearEndStreak = 0
            return 0f
        }

        // 回声基线取播放期麦克风电平的「快降慢升」包络：回声的音节起伏不会
        // 持续抬高基线，而近端人声贴近麦克风时会形成明显的电平跃升。参考电平
        // 只作辅助：句间静音或时钟漂移时它为 0，由麦克风实测基线兜底。
        val floor = echoFloor
        val nearEndFrame = speechProbability >= NEAR_END_PROBABILITY &&
            microphoneLevel >= referenceLevel * NEAR_END_REFERENCE_RATIO &&
            (floor == null || microphoneLevel >= floor + NEAR_END_LEVEL_MARGIN)
        if (nearEndFrame) {
            nearEndStreak++
            if (nearEndStreak >= NEAR_END_FRAMES) nearEndLatched = true
        } else {
            nearEndStreak = 0
        }
        if (nearEndLatched) {
            // 确认打断后保持放行到本句结束，避免逐帧抖动把人声切成碎片。
            if (speechProbability < NEAR_END_RELEASE_PROBABILITY) nearEndLatched = false
            return speechProbability
        }
        return 0f
    }

    private fun updateEchoFloor(level: Float) {
        val current = echoFloor
        echoFloor = when {
            current == null -> level
            level < current -> current - (current - level) * FLOOR_FALL
            else -> current + (level - current) * FLOOR_RISE
        }.coerceAtLeast(0f)
    }

    companion object {
        /** AEC 重新收敛的经验余量，与 speexdsp 300 ms 尾长不是同一概念。 */
        const val CONVERGE_WINDOW_NANOS = 800_000_000L

        /** 播完后的声学尾音期：混响、扬声器余振与 AEC 残留仍可能进 VAD。 */
        const val PLAYBACK_TAIL_NANOS = 400_000_000L

        /** 静置超过这么久才丢弃回声基线；短于此视为同一轮播报的句间间隙。 */
        const val PLAYBACK_IDLE_NANOS = 1_200_000_000L

        /** 近端人声的 VAD 置信门槛，明显高于切句器的 0.6。 */
        const val NEAR_END_PROBABILITY = 0.8f

        /** 麦克风电平至少要达到播放参考电平的这个比例，才可能是近端人声。 */
        const val NEAR_END_REFERENCE_RATIO = 0.65f

        /** 电平超出回声基线多少才认作近端语音（0..1 刻度，约 7 dB）。 */
        const val NEAR_END_LEVEL_MARGIN = 0.12f

        /** 连续命中的 32 ms 帧数，压掉回声里的瞬态尖峰。 */
        const val NEAR_END_FRAMES = 3

        /** 打断确认后，概率低于此值就解除放行闩锁。 */
        const val NEAR_END_RELEASE_PROBABILITY = 0.5f

        private const val FLOOR_FALL = 0.5f
        private const val FLOOR_RISE = 0.02f
    }
}
