package github.ponyhuang.gimi.data.speech.remote

/**
 * One captured PCM audio segment waiting to be transcribed.
 */
data class SpeechRecognitionRequest(
    val pcm16: ByteArray,
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val language: String = "auto",
)

/**
 * Connection and model selection used for one speech-recognition request.
 *
 * @property serviceId identifies vendor-specific wire protocols.
 */
data class SpeechRecognitionConfig(
    val serviceId: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
)

interface SpeechRecognitionGateway {
    suspend fun transcribe(
        config: SpeechRecognitionConfig,
        request: SpeechRecognitionRequest,
    ): String
}
