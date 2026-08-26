package github.ponyhuang.gimi.plugin.zhihu

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 知乎开放平台（developer.zhihu.com）API 客户端。
 *
 * 鉴权：`Authorization: Bearer <access_secret>` + `X-Request-Timestamp`（秒级时间戳）。
 * 响应包裹：`{Code, Message, Data}`（Code=0 成功）。
 * HTTP 用 JDK 自带 HttpURLConnection，JSON 用 Android 自带 org.json，插件零第三方依赖。
 */
internal class ZhihuApi(
    private val baseUrl: String = "https://developer.zhihu.com",
) {

    /** 热榜：返回 `Data`（含 `Total`、`Items`）。 */
    fun hotList(secret: String, limit: Int): JSONObject =
        data(get("/api/v1/content/hot_list", secret, mapOf("Limit" to limit.toString())))

    /** 站内搜索：返回 `Data`（含 `Items`、`HasMore`、`SearchHashId`）。 */
    fun zhihuSearch(secret: String, query: String, count: Int): JSONObject =
        data(
            get(
                "/api/v1/content/zhihu_search",
                secret,
                mapOf("Query" to query, "Count" to count.toString()),
            ),
        )

    /** 全网搜索：返回 `Data`。 */
    fun globalSearch(
        secret: String,
        query: String,
        count: Int,
        filter: String?,
        searchDb: String?,
    ): JSONObject {
        val params = linkedMapOf("Query" to query, "Count" to count.toString())
        filter?.takeIf(String::isNotBlank)?.let { params["Filter"] = it }
        searchDb?.takeIf(String::isNotBlank)?.let { params["SearchDB"] = it }
        return data(get("/api/v1/content/global_search", secret, params))
    }

    /** 直答：返回 OpenAI 风格 JSON（`choices[].message`）。 */
    fun zhida(secret: String, model: String, query: String): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "user").put("content", query))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
        return checked(post("/v1/chat/completions", secret, body.toString()))
    }

    /** 包裹校验 + 取 Data。 */
    private fun data(json: JSONObject): JSONObject = checked(json).optJSONObject("Data") ?: JSONObject()

    private fun checked(json: JSONObject): JSONObject {
        val code = json.optInt("Code", 0)
        if (code != 0) {
            throw IllegalStateException("知乎 API 错误 code=$code: ${json.optString("Message")}")
        }
        return json
    }

    private fun get(path: String, secret: String, query: Map<String, String>): JSONObject {
        val url = buildString {
            append(baseUrl).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
            }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return connection.readJson(secret)
    }

    private fun post(path: String, secret: String, body: String): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.readJson(secret)
    }

    private fun HttpURLConnection.readJson(secret: String): JSONObject {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("Authorization", "Bearer $secret")
        setRequestProperty("X-Request-Timestamp", (System.currentTimeMillis() / 1000).toString())
        setRequestProperty("Content-Type", "application/json")
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("知乎 API HTTP $code: $body")
        }
        return JSONObject(body)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时。 */
        const val READ_TIMEOUT_MS: Int = 30_000
    }
}
