package github.ponyhuang.gimi.core.audio

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/**
 * 一次完整语句的音频与其 VAD 画像。画像供 [UtteranceCommitGate] 在送识别前
 * 判断该段是否值得提交：嘈杂环境的人群说话声同样能通过起始确认，只有段级
 * 统计才能在识别前再拦一道。
 *
 * @property pcm16 语句完整 PCM（含 pre-roll 与句末静默），16 kHz 单声道。
 * @property peakProbability 段内最高 VAD 概率（切句器输入概率，已是回声门控后的值）。
 * @property speechFrameCount 段内概率 ≥ [VoiceActivitySegmenter.SPEECH_FRAME_PROBABILITY] 的帧数。
 * @property frameCount 段内总帧数（含起始确认与句末静默）。
 * @property peakLevel 段内最高麦克风电平（0..1，刻度同 [calculatePcm16Level]）。
 */
data class UtteranceSegment(
    val pcm16: ByteArray,
    val peakProbability: Float,
    val speechFrameCount: Int,
    /** 段内概率 ≥ 结束阈值的「非静默」帧数;提交门占比用它做分母,避免句末静默尾稀释短语句。 */
    val voicedFrameCount: Int,
    val frameCount: Int,
    val peakLevel: Float,
)

/**
 * 将固定时长的 PCM 帧与 VAD 概率转换为开始说话和完整语句事件。
 * 输入为 16 kHz 单声道 PCM16；模型只负责概率，切句、有限缓冲与段画像统计由此类统一管理。
 * `accept` 由录音处理线程调用，`reset` 可能由路由切换、会话收起等外部线程触发；
 * 两者都需要持锁，否则跨线程重置会把上一会话的语音片段拼进新会话。
 */
class VoiceActivitySegmenter(
    private val onSpeechStarted: () -> Unit,
    private val onUtterance: (UtteranceSegment) -> Unit,
) {
    /** pre-roll 环携带概率与电平：起始命中时无需重算即可汇总出与音频严格同窗的语句画像。 */
    private class PreRollFrame(val pcm: ByteArray, val probability: Float, val level: Float)

    private val preRoll = ArrayDeque<PreRollFrame>()
    private val utterance = ByteArrayOutputStream()
    private var positiveFrames = 0
    private var silentFrames = 0
    private var speaking = false
    private var peakProbability = 0f
    private var speechFrames = 0
    private var voicedFrames = 0
    private var peakLevel = 0f
    private var frameCount = 0

    /** 每帧 512 个采样（32 ms）；概率必须来自处理当前音频流的同一 VAD 实例。 */
    @Synchronized
    fun accept(pcm16: ByteArray, speechProbability: Float) {
        require(pcm16.size == FRAME_BYTES)
        require(speechProbability.isFinite() && speechProbability in 0f..1f)
        if (!speaking) {
            preRoll.addLast(PreRollFrame(pcm16.copyOf(), speechProbability, calculatePcm16Level(pcm16)))
            while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            positiveFrames = if (speechProbability >= START_THRESHOLD) positiveFrames + 1 else 0
            if (positiveFrames < START_FRAMES) return
            speaking = true
            silentFrames = 0
            preRoll.forEach { frame -> include(frame.pcm, frame.probability, frame.level) }
            preRoll.clear()
            onSpeechStarted()
            return
        }

        include(pcm16, speechProbability, calculatePcm16Level(pcm16))
        silentFrames = if (speechProbability < END_THRESHOLD) silentFrames + 1 else 0
        // 最大长度限制同样作用于持续说话，避免没有句末时无限占用内存。
        if (silentFrames >= END_FRAMES || utterance.size() >= MAX_UTTERANCE_BYTES) {
            val segment = UtteranceSegment(
                pcm16 = utterance.toByteArray(),
                peakProbability = peakProbability,
                speechFrameCount = speechFrames,
                voicedFrameCount = voicedFrames,
                frameCount = frameCount,
                peakLevel = peakLevel,
            )
            reset()
            onUtterance(segment)
        }
    }

    /** 静音、退出或输入设备重建时丢弃未完成语句，禁止跨会话拼接。 */
    @Synchronized
    fun reset() {
        preRoll.clear()
        utterance.reset()
        positiveFrames = 0
        silentFrames = 0
        speaking = false
        peakProbability = 0f
        speechFrames = 0
        voicedFrames = 0
        peakLevel = 0f
        frameCount = 0
    }

    private fun include(pcm: ByteArray, probability: Float, level: Float) {
        utterance.write(pcm)
        frameCount++
        if (probability > peakProbability) peakProbability = probability
        if (probability >= SPEECH_FRAME_PROBABILITY) speechFrames++
        if (probability >= END_THRESHOLD) voicedFrames++
        if (level > peakLevel) peakLevel = level
    }

    companion object {
        const val FRAME_BYTES = 512 * 2
        private const val START_THRESHOLD = .75f
        private const val END_THRESHOLD = .35f

        /**
         * 起始确认 6 帧(192ms):短问候(hi/hello/你好)的浊音段只有 200-300ms,
         * 更长的确认窗会把它们整个吞掉;瞬态防护由识别提交门兜底。
         */
        private const val START_FRAMES = 6
        private const val END_FRAMES = 22
        private const val PRE_ROLL_FRAMES = 12
        private const val MAX_UTTERANCE_BYTES = 16_000 * 2 * 30

        /** 画像统计「有效语音帧」的概率下限；提交门按此口径计算帧数与占比。 */
        const val SPEECH_FRAME_PROBABILITY = .8f
    }
}
