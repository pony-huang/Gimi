package github.ponyhuang.gimi.data.speech.remote

import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Calls MiniMax ASR (`POST /v1/speech_to_text`) with the WAV container required by
 * the vendor. The endpoint is vendor-owned and therefore intentionally does not
 * reuse the configurable chat base URL.
 */
class MinimaxSpeechRecognitionGateway(
    private val okHttpClient: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : SpeechRecognitionGateway {

    override suspend fun transcribe(
        config: SpeechRecognitionConfig,
        request: SpeechRecognitionRequest,
    ): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.modelId)
            .addFormDataPart(
                "file",
                "recording.wav",
                wav.toRequestBody(WAV_MEDIA_TYPE),
            )
            .addFormDataPart("response_format", "json")
            .addFormDataPart("stream", "false")
            .build()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .apply {
                // MiniMax treats an omitted language as mixed-language recognition.
                if (request.language.isNotBlank() && request.language != "auto") {
                    header("language", request.language)
                }
            }
            .post(body)
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("MiniMax 语音识别请求失败：HTTP ${response.code}：$responseBody")
            }
            JsonParser.parseString(responseBody)
                .asJsonObject
                .get("text")
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?.takeIf(String::isNotBlank)
                ?: throw IOException("MiniMax 语音识别未返回文本")
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.minimax.cn/v1/speech_to_text"
        private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}
