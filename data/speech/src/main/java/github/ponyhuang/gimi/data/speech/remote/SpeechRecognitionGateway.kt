package github.ponyhuang.gimi.data.speech.remote

data class SpeechRecognitionRequest(
    val pcm16: ByteArray,
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val language: String = "auto",
)

data class SpeechRecognitionConfig(
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
