package github.ponyhuang.asssistantai.agent.tools.official.glm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

/** One entry of the GLM `search_result` array exposed to the model. */
internal data class GlmSearchResult(
    val title: String,
    val link: String,
    val content: String,
    val media: String,
    val publishDate: String,
)

/**
 * Client for the GLM Web Search API (`POST {base}/web_search`).
 *
 * Kept separate from [GlmWebSearchTool] so the request/response mapping can be
 * unit-tested with a canned [OkHttpClient], mirroring the Kimi formula
 * manifest/tool split.
 */
internal class GlmWebSearchApi(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun search(
        query: String,
        count: Int?,
        recencyFilter: String?,
        contentSize: String?,
    ): List<GlmSearchResult> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("search_query", query)
            put("search_engine", DEFAULT_SEARCH_ENGINE)
            put("search_intent", false)
            count?.let { put("count", it) }
            recencyFilter?.let { put("search_recency_filter", it) }
            contentSize?.let { put("content_size", it) }
        }

        val request = Request.Builder()
            .url(webSearchUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code} from GLM web search: $body" }
            parseResults(body)
        }
    }

    private fun parseResults(body: String): List<GlmSearchResult> {
        val root = Json.parseToJsonElement(body).jsonObject
        return root["search_result"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            GlmSearchResult(
                title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                link = item["link"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                media = item["media"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publishDate = item["publish_date"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_SEARCH_ENGINE = "search_std"
        private const val DEFAULT_WEB_SEARCH_URL =
            "https://open.bigmodel.cn/api/paas/v4/web_search"
        private const val ANTHROPIC_BASE_URL_SUFFIX = "/api/anthropic"

        /**
         * GLM 的 Anthropic 协议地址（…/api/anthropic）不承载 web_search 端点，
         * 该协议下回退到 paas v4 默认地址；其余情况在标准地址后拼接路径。
         */
        internal fun webSearchUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith(ANTHROPIC_BASE_URL_SUFFIX)) {
                DEFAULT_WEB_SEARCH_URL
            } else {
                "$trimmed/web_search"
            }
        }
    }
}
