package github.ponyhuang.asssistantai.domain.speech.repository

import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackState
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

    fun clearSession()
}
