package github.ponyhuang.gimi.domain.speech.repository

import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
