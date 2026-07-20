package github.ponyhuang.asssistantai.data.speech.remote

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MiMoSpeechSynthesisGateway(
    private val httpClient: OkHttpClient,
    private val gson: Gson = Gson(),
) : SpeechSynthesisGateway {
    override fun synthesize(config: SpeechSynthesisConfig, text: String): Flow<ByteArray> = flow {
        val payload = JsonObject().apply {
            addProperty("model", config.modelId)
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "assistant", "content" to text))))
            add(
                "audio",
                JsonObject().apply {
                    addProperty("format", "pcm16")
                    addProperty("voice", config.voiceId)
                },
            )
            addProperty("stream", true)
        }
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("api-key", config.apiKey)
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = httpClient.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(500).orEmpty()
                    throw IOException(
                        "语音合成失败（HTTP ${response.code}）" +
                            detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
                    )
                }
                val source = response.body?.source() ?: throw IOException("语音合成响应为空")
                var emittedAudio = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") {
                        if (data == "[DONE]") break
                        continue
                    }
                    val audioData = runCatching {
                        gson.fromJson(data, JsonObject::class.java)
                            .getAsJsonArray("choices")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.getAsJsonObject("audio")
                            ?.get("data")
                            ?.asString
                    }.getOrNull()
                    if (!audioData.isNullOrBlank()) {
                        val bytes = runCatching { Base64.decode(audioData, Base64.DEFAULT) }
                            .getOrElse { throw IOException("语音合成返回了无效音频数据", it) }
                        if (bytes.isNotEmpty()) {
                            emittedAudio = true
                            emit(bytes)
                        }
                    }
                }
                if (!emittedAudio) throw IOException("语音合成未返回音频数据")
            }
        } finally {
            cancellation.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
