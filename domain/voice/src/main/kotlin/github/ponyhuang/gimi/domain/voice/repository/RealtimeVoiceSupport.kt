package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/**
 * Realtime 模式的上行音频契约：采样率与是否需要重采样，随厂商协议而异。
 *
 * @property sampleRateHz 厂商服务端接受的上行 PCM 采样率（Hz）。
 * @property resampled 为 true 时门面把 16 kHz 采集音频重采样到 [sampleRateHz]；为 false 时直喂。
 */
data class RealtimeUplink(val sampleRateHz: Int, val resampled: Boolean)

/**
 * 原生全双工实时语音适配器的自述能力。
 *
 * 只有实现了常驻双工会话的服务才提供这份声明；目录里带 [github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability.Realtime]
 * 标记、但没有对应声明的模型不得被选为 Realtime 默认模型——否则它们会静默回落到级联链路
 * （而级联链路要求 STT/TTS 能力，Realtime 模型并不声明），会话根本起不来。
 *
 * 厂商协议细节（端点、事件格式、上行采样率、音色集）由实现方持有，调用方只按服务标识查询。
 */
interface RealtimeVoiceSupport {
    /** 本适配器服务的服务标识。 */
    val serviceId: String

    /**
     * 与模型无关的厂商内置会话音色。
     *
     * 音色集随模型代际变化时留空，改由 [voicesFor] 按模型给出——把「某个模型能用哪些音色」
     * 压成一张服务级总表，只会让用户选到服务端拒绝的音色。
     */
    val builtInVoices: List<TtsVoice>

    /**
     * 指定 Realtime 模型可选的系统音色。
     *
     * 默认回落到 [builtInVoices]；音色集跨代际不同（如千问 3.0 与 3.1）的实现方需要覆写。
     */
    fun voicesFor(modelId: String): List<TtsVoice> = builtInVoices

    /** 是否允许填写内置音色之外的 voice_id（声音复刻等厂商自定义音色）。 */
    val customVoiceAllowed: Boolean

    /**
     * 某个 Realtime 模型的缺省会话音色。
     *
     * 同一厂商的不同代际模型音色集不同（Qwen 3.0 与 3.1 各自一套），因此缺省音色按模型解析，
     * 不能只按服务给一个值。未知模型返回 null，由调用方回退到 [builtInVoices] 或拒绝配置。
     */
    fun defaultVoiceFor(modelId: String): String?

    /** 该服务的上行音频契约。 */
    val uplink: RealtimeUplink
}
