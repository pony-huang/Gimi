package github.ponyhuang.gimi.data.agent.tools.official.minimax

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * MiniMax 官方图像生成接口的请求客户端。
 *
 * 接口固定使用中国站图像端点，避免聊天模型的可配置 base URL 被误用于图像 API。
 */
internal class MinimaxImageGenerationApi(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {
    suspend fun generate(
        prompt: String,
        model: String,
        aspectRatio: String?,
        subjectReference: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("response_format", "url")
            aspectRatio?.let { put("aspect_ratio", it) }
            subjectReference?.let { imageFile ->
                put("subject_reference", buildJsonArray {
                    add(buildJsonObject {
                        put("type", SUBJECT_TYPE_CHARACTER)
                        put("image_file", imageFile)
                    })
                })
            }
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = requireNotNull(response.body) { "MiniMax image generation returned an empty body" }.string()
            check(response.isSuccessful) { "HTTP ${response.code} from MiniMax image generation: $body" }
            parseImageUrls(body)
        }
    }

    private fun parseImageUrls(body: String): List<String> {
        val root = Json.parseToJsonElement(body).jsonObject
        val baseResponse = root["base_resp"]?.jsonObject
        val statusCode = baseResponse?.get("status_code")?.jsonPrimitive?.contentOrNull
        check(statusCode == null || statusCode == "0") {
            "MiniMax image generation failed: ${baseResponse?.get("status_msg")?.jsonPrimitive?.contentOrNull.orEmpty()}"
        }
        return root["data"]?.jsonObject?.get("image_urls")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
            .also { urls -> check(urls.isNotEmpty()) { "MiniMax image generation returned no image URLs" } }
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SUBJECT_TYPE_CHARACTER = "character"
        const val DEFAULT_ENDPOINT = "https://api.minimax.cn/v1/image_generation"
    }
}
