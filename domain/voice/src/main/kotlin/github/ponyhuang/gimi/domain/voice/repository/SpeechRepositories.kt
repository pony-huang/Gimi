package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.model.SpeechContextMessage
import github.ponyhuang.gimi.domain.voice.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 音色列表能力。
 *
 * 与其它语音能力一样按服务标识注册：新增厂商只需实现本接口并绑定，
 * 不需要修改任何集中路由。可实现为本地静态列表、远程 API 动态拉取，
 * 或结合本地数据做兜底。
 */
interface TtsVoiceProvider : CapabilityAdapter {
    override val capabilities: Set<ModelCapability>
        get() = setOf(ModelCapability.TTS)

    /** 获取指定厂商的模型音色列表。 */
    suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice>
}

interface TtsVoiceRepository {
    /**
     * 获取指定语音模型的音色列表（由仓储统一装配当前服务商的配置并路由到对应的 Provider）。
     *
     * 入参是完整的模型选择而不是服务 ID：厂商音色通常与模型绑定，只知道服务无法确定可用音色。
     */
    suspend fun getVoices(selection: ModelSelection): List<TtsVoice>
}

interface SpeechRecognitionRepository {
    val availability: Flow<Boolean>

    /**
     * 转写一段录音。
     *
     * @param pcm16 单声道 PCM16 音频。
     * @param context 可选的对话上下文，用于提升专有名词识别准确率；为空表示不携带上下文。
     */
    suspend fun transcribe(
        pcm16: ByteArray,
        context: List<SpeechContextMessage> = emptyList(),
    ): String

    /**
     * 流式转写：产出中间结果与最终结果。
     *
     * 服务未接入流式识别时只产出最终结果，因此调用方可以统一按流消费，
     * 不必因为厂商差异改写识别分支。
     *
     * @param pcm16 单声道 PCM16 音频。
     * @param context 可选的对话上下文，用于提升专有名词识别准确率；为空表示不携带上下文。
     */
    fun transcribeStreaming(
        pcm16: ByteArray,
        context: List<SpeechContextMessage> = emptyList(),
    ): Flow<RecognitionEvent>
}

interface SpeechSynthesisRepository {
    val availability: Flow<Boolean>

    fun isAvailable(): Boolean

    fun synthesize(text: String): Flow<ByteArray>

    fun cacheIdentity(): String?

    /**
     * 打开一次会话式合成。
     *
     * 当前服务未接入会话式合成时返回 null，调用方据此回退到一次性合成。
     */
    fun openSession(): SpeechSynthesisSession?
}

interface SpeechPlaybackRepository {
    val state: StateFlow<SpeechPlaybackState>

    /** 播放失败；按失败分类上报，界面据此本地化文案。 */
    val errors: SharedFlow<VoiceFailure>

    fun toggle(messageId: String, text: String)

    /** 显式开始播报指定内容（中断当前播报），供助理浮层自动播报使用。 */
    fun play(messageId: String, text: String)

    /** 显式停止当前播报。 */
    fun stop()

    fun clearSession()
}

/**
 * 语音播报全局偏好。开关跨会话持久化：开启后每轮 assistant 回复完成时自动朗读，
 * 不随新会话创建而重置。
 */
interface SpeechSettingsRepository {
    /** 自动语音播报开关；`true` 表示开启，首次安装默认关闭。 */
    val autoSpeakEnabled: StateFlow<Boolean>

    /** 写入自动播报开关，立即生效并持久化。 */
    fun setAutoSpeakEnabled(enabled: Boolean)
}
