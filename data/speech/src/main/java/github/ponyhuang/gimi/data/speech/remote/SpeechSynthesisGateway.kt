package github.ponyhuang.gimi.data.speech.remote

import kotlinx.coroutines.flow.Flow

data class SpeechSynthesisConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val voiceId: String,
)

interface SpeechSynthesisGateway {
    fun synthesize(config: SpeechSynthesisConfig, text: String): Flow<ByteArray>
}
