package github.ponyhuang.gimi.core.audio

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/**
 * 将固定时长的 PCM 帧与 VAD 概率转换为开始说话和完整语句事件。
 * 输入为 16 kHz 单声道 PCM16；模型只负责概率，切句和有限缓冲由此类统一管理。
 * `accept` 由录音处理线程调用，`reset` 可能由路由切换、会话收起等外部线程触发；
 * 两者都需要持锁，否则跨线程重置会把上一会话的语音片段拼进新会话。
 */
class VoiceActivitySegmenter(
    private val onSpeechStarted: () -> Unit,
    private val onUtterance: (ByteArray) -> Unit,
) {
    private val preRoll = ArrayDeque<ByteArray>()
    private val utterance = ByteArrayOutputStream()
    private var positiveFrames = 0
    private var silentFrames = 0
    private var speaking = false

    /** 每帧 512 个采样（32 ms）；概率必须来自处理当前音频流的同一 VAD 实例。 */
    @Synchronized
    fun accept(pcm16: ByteArray, speechProbability: Float) {
        require(pcm16.size == FRAME_BYTES)
        require(speechProbability.isFinite() && speechProbability in 0f..1f)
        if (!speaking) {
            preRoll.addLast(pcm16.copyOf())
            while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            positiveFrames = if (speechProbability >= START_THRESHOLD) positiveFrames + 1 else 0
            if (positiveFrames < START_FRAMES) return
            speaking = true
            silentFrames = 0
            preRoll.forEach { utterance.write(it) }
            preRoll.clear()
            onSpeechStarted()
            return
        }

        utterance.write(pcm16)
        silentFrames = if (speechProbability < END_THRESHOLD) silentFrames + 1 else 0
        // 最大长度限制同样作用于持续说话，避免没有句末时无限占用内存。
        if (silentFrames >= END_FRAMES || utterance.size() >= MAX_UTTERANCE_BYTES) {
            val audio = utterance.toByteArray()
            reset()
            onUtterance(audio)
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
    }

    companion object {
        const val FRAME_BYTES = 512 * 2
        private const val START_THRESHOLD = .6f
        private const val END_THRESHOLD = .35f
        private const val START_FRAMES = 5
        private const val END_FRAMES = 22
        private const val PRE_ROLL_FRAMES = 12
        private const val MAX_UTTERANCE_BYTES = 16_000 * 2 * 30
    }
}
