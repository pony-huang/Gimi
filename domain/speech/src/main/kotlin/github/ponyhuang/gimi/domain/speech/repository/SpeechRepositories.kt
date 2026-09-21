package github.ponyhuang.gimi.domain.speech.repository

import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TtsVoiceProvider {
    val serviceId: String

    /**
     * 获取指定厂商的模型音色列表。
     * 可实现为本地静态列表、远程 API 动态拉取，或结合本地数据做兜底。
     */
    suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice>
}

interface TtsVoiceRepository {
    /**
     * 获取指定厂商的模型音色列表（由仓储统一装配当前服务商的配置并路由到对应的 Provider）。
     */
    suspend fun getVoices(serviceId: String): List<TtsVoice>
}

interface SpeechRecognitionRepository {
    val availability: Flow<Boolean>

    suspend fun transcribe(pcm16: ByteArray): String
}

interface SpeechSynthesisRepository {
    val availability: Flow<Boolean>

    fun isAvailable(): Boolean

    fun synthesize(text: String): Flow<ByteArray>

    fun cacheIdentity(): String?
}

interface SpeechPlaybackRepository {
    val state: StateFlow<SpeechPlaybackState>
    val errors: SharedFlow<String>

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
    /** 自动语音播报开关；`true` 表示开启（默认值）。 */
    val autoSpeakEnabled: StateFlow<Boolean>

    /** 写入自动播报开关，立即生效并持久化。 */
    fun setAutoSpeakEnabled(enabled: Boolean)
}
