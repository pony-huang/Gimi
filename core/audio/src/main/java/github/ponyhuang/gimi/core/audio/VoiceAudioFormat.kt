package github.ponyhuang.gimi.core.audio

/**
 * 语音链路的音频格式契约。
 *
 * 输入（录音与识别）固定 16 kHz 单声道 PCM16；输出（合成与播放）固定 24 kHz 单声道 PCM16。
 * 播放器的 AudioTrack 配置与回声消除的参考缓冲必须取自同一份常量，避免两处各自硬编码后漂移。
 *
 * 常量放在 :core:audio 是因为它同时被核心音频模块（录音、回声参考）与数据层（播放器、协议适配器）
 * 消费，而这两者之间不存在共同的更高层模块。
 */
object VoiceAudioFormat {
    /** 录音与识别的输入采样率。 */
    const val INPUT_SAMPLE_RATE_HZ = 16_000

    /** 合成与播放的输出采样率。 */
    const val OUTPUT_SAMPLE_RATE_HZ = 24_000

    /** 每采样点的字节数；PCM16 为双字节。 */
    const val BYTES_PER_SAMPLE = 2

    /** 声道数；本应用的语音链路全程单声道。 */
    const val CHANNEL_COUNT = 1
}
