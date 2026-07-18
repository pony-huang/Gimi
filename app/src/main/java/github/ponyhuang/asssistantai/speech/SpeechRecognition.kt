package github.ponyhuang.asssistantai.speech

import github.ponyhuang.asssistantai.data.ModelServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

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

interface SpeechRecognitionClient {
    suspend fun transcribe(
        config: SpeechRecognitionConfig,
        request: SpeechRecognitionRequest,
    ): String
}

@Singleton
class SpeechRecognitionRepository @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val client: SpeechRecognitionClient,
) {
    fun isAvailable(): Boolean = resolveConfig() != null

    suspend fun transcribe(pcm16: ByteArray): String {
        require(pcm16.isNotEmpty()) { "没有录制到语音，请重试" }
        val config = resolveConfig() ?: error("请先在设置中选择可用的默认语音模型")
        return client.transcribe(
            config = config,
            request = SpeechRecognitionRequest(pcm16 = pcm16),
        ).trim().takeIf { it.isNotEmpty() } ?: error("语音识别未返回文本")
    }

    private fun resolveConfig(): SpeechRecognitionConfig? {
        val resolved = modelServices.resolveSpeechSelection(
            modelServices.defaultSpeechSelection.value,
        ) ?: return null
        return SpeechRecognitionConfig(
            baseUrl = resolved.provider.apiBaseUrl.trimEnd('/'),
            apiKey = resolved.provider.apiKey.trim(),
            modelId = resolved.model.modelId,
        )
    }
}
