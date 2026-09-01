package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Mem0 Platform 的统一 HTTP 边界，负责鉴权、JSON 请求和响应错误转换。 */
class Mem0ApiClient(
    private val httpClient: OkHttpClient,
    private val settingsRepository: MemorySettingsRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 向 [path] 提交 JSON 请求体并返回 JSON 对象响应。 */
    suspend fun post(path: String, body: JsonObject): JsonObject = request(
        Request.Builder()
            .url(url(path))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)),
    )

    /** 删除 [path] 指向的资源并返回可选 JSON 响应。 */
    suspend fun delete(path: String): JsonObject = request(
        Request.Builder()
            .url(url(path))
            .delete(),
    )

    private suspend fun request(builder: Request.Builder): JsonObject = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.configuration.value.apiKey
        require(apiKey.isNotBlank()) { "Mem0 API key is missing." }
        val request = builder
            .header("Authorization", "Token $apiKey")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Mem0 HTTP ${response.code}: ${body.take(200)}")
            if (body.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(body).jsonObject
        }
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.mem0.ai"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
