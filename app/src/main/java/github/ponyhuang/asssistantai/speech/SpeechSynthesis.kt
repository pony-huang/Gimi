package github.ponyhuang.asssistantai.speech

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
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

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String?,
    val gender: String?,
)

object MiMoTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("mimo_default", "MiMo-默认", null, null),
        TtsVoice("冰糖", "冰糖", "中文", "女声"),
        TtsVoice("茉莉", "茉莉", "中文", "女声"),
        TtsVoice("苏打", "苏打", "中文", "男声"),
        TtsVoice("白桦", "白桦", "中文", "男声"),
        TtsVoice("Mia", "Mia", "English", "女声"),
        TtsVoice("Chloe", "Chloe", "English", "女声"),
        TtsVoice("Milo", "Milo", "English", "男声"),
        TtsVoice("Dean", "Dean", "English", "男声"),
    )
}

data class SpeechSynthesisConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val voiceId: String,
)

interface SpeechSynthesisClient {
    fun synthesize(config: SpeechSynthesisConfig, text: String): Flow<ByteArray>
}

class MiMoSpeechSynthesisClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) : SpeechSynthesisClient {
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
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
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
                    throw IOException("语音合成失败（HTTP ${response.code}）${detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
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

@Singleton
class SpeechSynthesisRepository @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val client: SpeechSynthesisClient,
) {
    fun isAvailable(): Boolean = resolveConfig() != null

    fun synthesize(text: String): Flow<ByteArray> {
        val normalized = text.trim().takeIf { it.isNotEmpty() } ?: error("没有可朗读的回复内容")
        val config = resolveConfig() ?: error("请先在设置中选择可用的默认语音播放模型")
        return client.synthesize(config, normalized)
    }

    fun cacheIdentity(): String? = resolveConfig()?.let {
        "${it.baseUrl}|${it.modelId}|${it.voiceId}"
    }

    private fun resolveConfig(): SpeechSynthesisConfig? {
        val resolved = modelServices.resolveTtsSelection(
            modelServices.defaultTtsSelection.value,
        ) ?: return null
        return SpeechSynthesisConfig(
            baseUrl = resolved.provider.apiBaseUrl.trimEnd('/'),
            apiKey = resolved.provider.apiKey.substringBefore(',').trim(),
            modelId = resolved.model.modelId,
            voiceId = modelServices.defaultTtsVoice.value,
        )
    }
}
