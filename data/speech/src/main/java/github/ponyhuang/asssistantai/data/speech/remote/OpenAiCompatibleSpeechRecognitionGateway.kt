package github.ponyhuang.asssistantai.data.speech.remote

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleSpeechRecognitionGateway(
    private val okHttpClient: OkHttpClient,
) : SpeechRecognitionGateway {
    private val gson = Gson()

    override suspend fun transcribe(
        config: SpeechRecognitionConfig,
        request: SpeechRecognitionRequest,
    ): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val body = gson.toJson(
            mapOf(
                "model" to config.modelId,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_audio",
                                "input_audio" to mapOf(
                                    "data" to Base64.encodeToString(wav, Base64.NO_WRAP),
                                    "format" to "wav",
                                ),
                            ),
                        ),
                    ),
                ),
                "asr_options" to mapOf("language" to request.language),
            ),
        )
        val httpRequest = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(httpRequest).execute().use { response ->
            check(response.isSuccessful) {
                "语音识别请求失败：HTTP ${response.code}"
            }
            val responseBody = checkNotNull(response.body).string()
            JsonParser.parseString(responseBody)
                .asJsonObject
                .getAsJsonArray("choices")
                ?.asSequence()
                ?.mapNotNull { choice ->
                    choice.asJsonObject
                        .getAsJsonObject("message")
                        ?.get("content")
                        ?.takeUnless { it.isJsonNull }
                        ?.asString
                }
                ?.firstOrNull(String::isNotBlank)
                ?: error("语音识别未返回文本")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun encodePcm16Wav(
    pcm16: ByteArray,
    sampleRateHz: Int,
    channelCount: Int,
): ByteArray {
    require(sampleRateHz > 0) { "sampleRateHz must be positive" }
    require(channelCount > 0) { "channelCount must be positive" }
    val bitsPerSample = 16
    val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8
    return ByteArray(44 + pcm16.size).also { output ->
        fun ascii(offset: Int, value: String) {
            value.forEachIndexed { index, char -> output[offset + index] = char.code.toByte() }
        }
        fun littleEndian16(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
        }
        fun littleEndian32(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
            output[offset + 2] = (value ushr 16).toByte()
            output[offset + 3] = (value ushr 24).toByte()
        }

        ascii(0, "RIFF")
        littleEndian32(4, 36 + pcm16.size)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleEndian32(16, 16)
        littleEndian16(20, 1)
        littleEndian16(22, channelCount)
        littleEndian32(24, sampleRateHz)
        littleEndian32(28, byteRate)
        littleEndian16(32, blockAlign)
        littleEndian16(34, bitsPerSample)
        ascii(36, "data")
        littleEndian32(40, pcm16.size)
        pcm16.copyInto(output, destinationOffset = 44)
    }
}
