package github.ponyhuang.gimi.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户症状的回归回路：助手播报时麦克风只录到自己的回声（本地 AEC 未消干净），
 * 这些帧若以原始 VAD 概率进入切句器，就会触发 speechStarted 并在静音后吐出
 * 一整段回声当作「用户这一轮说的话」，送进 ASR → LLM → TTS，形成自激循环。
 *
 * 这里把门控与真实的 [VoiceActivitySegmenter] 串起来，断言「播报期间不会产生
 * 新一轮用户输入」，而不是只断言某个布尔值。
 */
class PlaybackEchoGuardTest {

    @Test
    fun playbackEchoNeverOpensANewTurn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        var speechStarts = 0
        var utterances = 0
        val segmenter = VoiceActivitySegmenter(
            onSpeechStarted = { speechStarts++ },
            onUtterance = { utterances++ },
        )
        guard.onAecReset()

        // 会话建立后，识别 + 生成 + 首个 TTS 包远晚于 800 ms；播报此刻才开始。
        clock.advance(1_500_000_000L)
        feed(clock, guard, segmenter, frames = 62, playing = true,
            probability = 0.92f, microphoneLevel = 0.42f, referenceLevel = 0.78f)
        // 播报结束后环境安静：旧实现会在这里把积攒的回声整段交给 ASR。
        feed(clock, guard, segmenter, frames = 25, playing = false,
            probability = 0.05f, microphoneLevel = 0.02f, referenceLevel = 0f)

        assertTrue(
            "播报回声触发了 $speechStarts 次用户开口、$utterances 段送检音频，助手会自打断并重复回答",
            speechStarts == 0 && utterances == 0,
        )
    }

    /**
     * 最小化后的对照：即便播报紧跟会话启动、收敛窗口完整覆盖播放开头，
     * 超过 800 ms 的长播报仍会失控。证伪「只把窗口起点改对就够了」。
     */
    @Test
    fun echoAfterConvergenceWindowStillDoesNotOpenANewTurn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        var speechStarts = 0
        val segmenter = VoiceActivitySegmenter(
            onSpeechStarted = { speechStarts++ },
            onUtterance = {},
        )
        guard.onAecReset()
        feed(clock, guard, segmenter, frames = 62, playing = true,
            probability = 0.92f, microphoneLevel = 0.42f, referenceLevel = 0.78f)
        assertEquals("收敛窗口结束后长播报的回声仍被判成用户开口", 0, speechStarts)
    }

    @Test
    fun playbackStartingLongAfterSessionStartIsStillGuarded() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)

        // 收敛窗口必须从「开始播放」起算，而不是从会话启动起算。
        val first = guard.gate(isPlaying = true, speechProbability = 0.95f,
            microphoneLevel = 0.42f, referenceLevel = 0.78f)
        assertTrue("播放起始的 AEC 收敛期没有保护", first == 0f)
    }

    @Test
    fun echoLevelFluctuationDoesNotOpenANewTurn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)

        // 播报收敛期建立回声基线。
        repeat(30) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        // 正常语音音节的电平起伏不应被误认成近端人声。
        val levels = floatArrayOf(0.40f, 0.47f, 0.43f, 0.49f, 0.41f, 0.46f, 0.44f, 0.48f)
        repeat(40) { index ->
            clock.advance(FRAME_NANOS)
            val gated = guard.gate(true, 0.94f, levels[index % levels.size], 0.78f)
            assertTrue("回声起伏在第 $index 帧被当成近端语音", gated < 0.6f)
        }
    }

    @Test
    fun idleMicrophoneIsNeverSuppressed() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(2_000_000_000L)
        assertEquals(0.91f,
            guard.gate(false, 0.91f, 0.55f, 0f), 0.0001f)
    }

    @Test
    fun silenceBetweenPlayedSentencesAllowsBargeIn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(2_000_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.3f, 0.05f, 0f)
        }
        // 句间没有播放参考能量：回声基线也低，连续 3 帧高置信人声仍应放行打断。
        var released = false
        repeat(PlaybackEchoGuard.NEAR_END_FRAMES) {
            clock.advance(FRAME_NANOS)
            if (guard.gate(true, 0.9f, 0.6f, 0f) >= 0.9f) released = true
        }
        assertTrue("句间静音时开口必须能打断播报", released)
    }

    @Test
    fun loudNearEndSpeechStillBargesIn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        var released = false
        repeat(8) {
            clock.advance(FRAME_NANOS)
            val gated = guard.gate(true, 0.96f, 0.72f, 0.78f)
            if (gated >= 0.9f) released = true
        }
        assertTrue("明显强于回声的近端人声应能打断播报", released)
    }

    @Test
    fun laterSegmentsOfOneReplyDoNotRestartConvergenceWindow() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(2_000_000_000L)
        // 第一句走完收敛窗口，整段按句间静音处理（无回声源）。
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.3f, 0.05f, 0f)
        }
        // 级联模式逐句播放的短间隙。
        repeat(3) {
            clock.advance(FRAME_NANOS)
            guard.gate(false, 0.1f, 0.02f, 0f)
        }
        // 第二句开头的静音段仍应可打断：AEC 滤波器在会话期间并未重建，
        // 不该每句都重新压一个 800 ms 窗口，否则整轮播报都无法打断。
        var released = false
        repeat(PlaybackEchoGuard.NEAR_END_FRAMES) {
            clock.advance(FRAME_NANOS)
            if (guard.gate(true, 0.9f, 0.6f, 0f) >= 0.9f) released = true
        }
        assertTrue("同一轮播报的后续句子不应重开收敛窗口", released)
    }

    @Test
    fun playbackStartingWithSilenceDoesNotMistakeLoudEchoForNearEnd() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        // 收敛期只有静音：回声基线还没有反映真实回声电平，不能把随后
        // 突然响起的 TTS 回声误判成「用户比回声更响」。
        repeat(30) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.3f, 0.02f, 0f)
        }
        repeat(20) {
            clock.advance(FRAME_NANOS)
            val gated = guard.gate(true, 0.95f, 0.42f, 0.78f)
            assertTrue("回声基线未稳时把播报回声当成近端人声", gated < 0.6f)
        }
    }

    /**
     * 播放刚结束时 `playing` 已置 false，但房间混响与 AEC 残留仍在。
     * 旧实现在 `!isPlaying` 分支直接返回原始概率，这段尾音会立刻触发新一轮。
     */
    @Test
    fun tailEchoAfterPlaybackEndsDoesNotOpenANewTurn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        var speechStarts = 0
        var utterances = 0
        val segmenter = VoiceActivitySegmenter(
            onSpeechStarted = { speechStarts++ },
            onUtterance = { utterances++ },
        )
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        feed(clock, guard, segmenter, frames = 62, playing = true,
            probability = 0.92f, microphoneLevel = 0.42f, referenceLevel = 0.78f)
        // 硬件队列排空后残留的混响尾音：VAD 置信高，但电平仍在回声基线附近。
        feed(clock, guard, segmenter, frames = 12, playing = false,
            probability = 0.92f, microphoneLevel = 0.30f, referenceLevel = 0f)
        feed(clock, guard, segmenter, frames = 25, playing = false,
            probability = 0.05f, microphoneLevel = 0.02f, referenceLevel = 0f)

        assertTrue(
            "播完后的混响尾音触发了 $speechStarts 次用户开口、$utterances 段送检音频",
            speechStarts == 0 && utterances == 0,
        )
    }

    /**
     * 播放参考电平由 `getTimestamp` / `playbackHeadPosition` 锚定时钟，锚点漂移时
     * 会虚假返回静音。把它当作「是否撤防」的开关，等于在播报最响时全量放行。
     */
    @Test
    fun referenceLevelDropoutDoesNotDisableGuardDuringPlayback() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        repeat(20) { index ->
            clock.advance(FRAME_NANOS)
            val gated = guard.gate(true, 0.92f, 0.42f, 0f)
            assertTrue("参考电平掉到 0 后第 $index 帧撤防，回声被当成用户开口", gated < 0.6f)
        }
    }

    @Test
    fun tailPeriodStillAllowsLoudNearEndBargeIn() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        // 尾音期常驻判定不能把「播完后用户立刻开口」一起压掉。
        var released = false
        repeat(6) {
            clock.advance(FRAME_NANOS)
            if (guard.gate(false, 0.96f, 0.85f, 0f) >= 0.9f) released = true
        }
        assertTrue("播完后的近端人声仍应能打断/开启下一轮", released)
    }

    @Test
    fun tailRiskCoversReverbAndExpires() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        clock.advance(FRAME_NANOS)
        guard.gate(false, 0.92f, 0.30f, 0f)
        assertTrue("播完 400 ms 内上行门控也必须保持", guard.tailRisk())
        clock.advance(PlaybackEchoGuard.PLAYBACK_TAIL_NANOS)
        assertTrue("尾音期结束后应恢复零延迟直通", !guard.tailRisk())
    }

    @Test
    fun longIdleReturnsToZeroLatencyPassThrough() {
        val clock = FakeClock()
        val guard = PlaybackEchoGuard(clock)
        guard.onAecReset()
        clock.advance(1_500_000_000L)
        repeat(40) {
            clock.advance(FRAME_NANOS)
            guard.gate(true, 0.92f, 0.42f, 0.78f)
        }
        clock.advance(FRAME_NANOS)
        guard.gate(false, 0.92f, 0.30f, 0f)
        clock.advance(PlaybackEchoGuard.PLAYBACK_IDLE_NANOS)
        assertEquals("真正静置后不应再有任何门控延迟", 0.9f,
            guard.gate(false, 0.9f, 0.55f, 0f), 0.0001f)
    }

    private fun feed(
        clock: FakeClock,
        guard: PlaybackEchoGuard,
        segmenter: VoiceActivitySegmenter,
        frames: Int,
        playing: Boolean,
        probability: Float,
        microphoneLevel: Float,
        referenceLevel: Float,
    ) {
        val silence = ByteArray(VoiceActivitySegmenter.FRAME_BYTES)
        repeat(frames) {
            clock.advance(FRAME_NANOS)
            val gated = guard.gate(playing, probability, microphoneLevel, referenceLevel)
            segmenter.accept(silence, gated)
        }
    }

    private class FakeClock : () -> Long {
        private var now = 1_000_000_000L
        override fun invoke(): Long = now
        fun advance(nanos: Long) { now += nanos }
    }

    private companion object {
        const val FRAME_NANOS = 32_000_000L
    }
}
