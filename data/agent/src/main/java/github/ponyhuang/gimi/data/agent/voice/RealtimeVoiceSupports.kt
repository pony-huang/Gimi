package github.ponyhuang.gimi.data.agent.voice

import github.ponyhuang.gimi.domain.voice.model.OpenAiRealtimeVoices
import github.ponyhuang.gimi.domain.voice.model.QwenRealtimeVoices
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI Realtime 的原生适配器声明。
 *
 * OpenAI 的会话音色集固定为内置音色，不接受自定义 voice_id（自定义音色需上传样本走
 * Voice Consents，不在本链路内），因此这里只给下拉不给自由输入。
 */
@Singleton
class OpenAiRealtimeVoiceSupport @Inject constructor() : RealtimeVoiceSupport {
    override val serviceId: String = OPENAI_SPEECH_SERVICE_ID
    override val builtInVoices = OpenAiRealtimeVoices.all
    override val customVoiceAllowed: Boolean = false
    override val uplink: RealtimeUplink = RealtimeUplink(sampleRateHz = 24_000, resampled = true)

    /** OpenAI 两个 Realtime 模型共用同一套音色，缺省音色与模型无关。 */
    override fun defaultVoiceFor(modelId: String): String = OpenAiRealtimeVoices.DEFAULT_VOICE
}

/**
 * 百炼 Qwen-Audio Realtime 的原生适配器声明。
 *
 * 音色集按代际分两套，跨代际使用会被服务端拒绝（`InvalidParameter`）：
 * 3.1 认 `longanqian_v3.1` 加上 3.1 TTS 的全部系统音色，3.0 只认固定的五个无后缀旧音色。
 * 两套表都由 [QwenRealtimeVoices] 按模型给出——同一服务下不同模型的可选音色并不相同，
 * 因此不能只暴露一张服务级音色表。
 */
@Singleton
class QwenRealtimeVoiceSupport @Inject constructor() : RealtimeVoiceSupport {
    override val serviceId: String = QWEN_AUDIO_SERVICE_ID

    /** 音色集随模型变化，服务级没有统一的内置音色表。 */
    override val builtInVoices = emptyList<TtsVoice>()

    override val customVoiceAllowed: Boolean = true
    override val uplink: RealtimeUplink = RealtimeUplink(sampleRateHz = 16_000, resampled = false)

    /** 按模型代际给可选系统音色；未收录的模型没有可选音色，只剩自由输入。 */
    override fun voicesFor(modelId: String): List<TtsVoice> = QwenRealtimeVoices.forModel(modelId)

    /**
     * 按模型代际给出缺省音色。
     *
     * 3.0 代际用无后缀的 `longanqian`；其余（含 3.1 与用户自定义模型 ID）用 `longanqian_v3.1`。
     */
    override fun defaultVoiceFor(modelId: String): String = QwenRealtimeVoices.defaultFor(modelId)
}
