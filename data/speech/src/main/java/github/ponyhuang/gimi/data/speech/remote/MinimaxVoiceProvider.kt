package github.ponyhuang.gimi.data.speech.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.ponyhuang.gimi.domain.speech.model.MinimaxTtsVoices
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimax 音色提供者：
 * 支持通过官方接口 `POST /v1/get_voice` 动态拉取系统音色、克隆音色和生成音色；
 * 在未配置 API Key、网络异常或服务端报错时，优雅降级返回本地内置音色列表。
 *
 * 参考文档：https://platform.minimax.cn/docs/api-reference/voice-management-get
 */
class MinimaxVoiceProvider(
    private val httpClient: OkHttpClient,
    private val gson: Gson = Gson(),
    private val defaultEndpoint: String = DEFAULT_ENDPOINT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TtsVoiceProvider {

    override val serviceId: String = "minimax"

    override suspend fun getVoices(context: TtsVoiceContext): List<TtsVoice> = withContext(dispatcher) {
        if (context.apiKey.isBlank()) {
            return@withContext MinimaxTtsVoices.all
        }

        try {
            fetchRemoteVoices(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote Minimax voices, falling back to built-in voices", e)
            MinimaxTtsVoices.all
        }
    }

    private fun fetchRemoteVoices(context: TtsVoiceContext): List<TtsVoice> {
        val endpoint = resolveEndpoint(context.baseUrl)
        val payload = JsonObject().apply {
            addProperty("voice_type", "all")
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${context.apiKey}")
            .header("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Minimax voice list HTTP error: ${response.code}")
            }
            val bodyString = response.body?.string()
                ?: throw IOException("Empty response body from Minimax voice list")

            val root = gson.fromJson(bodyString, JsonObject::class.java)
            val baseResp = root.getAsJsonObject("base_resp")
            val statusCode = baseResp?.get("status_code")?.asInt ?: 0
            if (statusCode != 0) {
                val statusMsg = baseResp?.get("status_msg")?.asString.orEmpty()
                throw IOException("Minimax API error: status_code=$statusCode, msg=$statusMsg")
            }

            val systemVoices = parseSystemVoices(root)
            val cloningVoices = parseClonedOrGeneratedVoices(root, "voice_cloning", "克隆音色")
            val generationVoices = parseClonedOrGeneratedVoices(root, "voice_generation", "生成音色")

            val remoteVoices = systemVoices + cloningVoices + generationVoices
            return remoteVoices.ifEmpty { MinimaxTtsVoices.all }
        }
    }

    private fun parseSystemVoices(root: JsonObject): List<TtsVoice> {
        val array = root.getAsJsonArray("system_voice") ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val voiceId = obj.get("voice_id")?.asString ?: return@mapNotNull null
            val voiceName = obj.get("voice_name")?.asString ?: voiceId
            val descArray = obj.getAsJsonArray("description")
            val descriptions = descArray?.mapNotNull { it.asString } ?: emptyList()
            TtsVoice(
                id = voiceId,
                name = voiceName,
                language = null,
                gender = null,
                description = descriptions.joinToString(" / ").ifEmpty { null },
            )
        }
    }

    private fun parseClonedOrGeneratedVoices(
        root: JsonObject,
        arrayKey: String,
        categoryLabel: String,
    ): List<TtsVoice> {
        val array = root.getAsJsonArray(arrayKey) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val voiceId = obj.get("voice_id")?.asString ?: return@mapNotNull null
            val descArray = obj.getAsJsonArray("description")
            val descriptions = descArray?.mapNotNull { it.asString } ?: emptyList()
            val name = descriptions.firstOrNull()?.takeIf { it.isNotBlank() } ?: voiceId
            val createdTime = obj.get("created_time")?.asString

            val tags = mutableListOf(categoryLabel)
            if (!createdTime.isNullOrBlank()) {
                tags.add(createdTime)
            }
            if (descriptions.size > 1) {
                tags.add(descriptions.drop(1).joinToString(" / "))
            }

            TtsVoice(
                id = voiceId,
                name = name,
                language = null,
                gender = null,
                description = tags.joinToString(" · "),
            )
        }
    }

    private fun resolveEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.isNotEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            "$trimmed/get_voice"
        } else {
            defaultEndpoint
        }
    }

    companion object {
        private const val TAG = "MinimaxVoiceProvider"
        const val DEFAULT_ENDPOINT = "https://api.minimax.cn/v1/get_voice"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
