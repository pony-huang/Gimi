package github.ponyhuang.gimi.plugin.v2ex

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * V2EX 公开 API（v1，匿名只读）客户端。
 *
 * 基址 [baseUrl] 默认 [DEFAULT_BASE_URL]，可经插件配置覆盖为镜像（如
 * `https://global.v2ex.co/api`）以适配受限网络。HTTP 用 JDK 自带 HttpURLConnection，
 * JSON 用 Android 自带 org.json，插件零第三方依赖。
 */
internal class V2exApi(var baseUrl: String = DEFAULT_BASE_URL) {

    /** 热门主题。 */
    fun hotTopics(): JSONArray = topicArray(get("/topics/hot.json"))

    /** 最新主题（接口返回最近约 20 条）。 */
    fun latestTopics(): JSONArray = topicArray(get("/topics/latest.json"))

    /** 指定节点下的主题；show.json 对单条返回对象、对多条返回数组，统一成数组。 */
    fun nodeTopics(nodeName: String): JSONArray =
        topicArray(parseResponse(get("/topics/show.json", mapOf("node_name" to nodeName))))

    /** 单主题详情（含正文）；接口按 id 查询时返回单对象，取第一条。 */
    fun topic(topicId: Int): JSONObject =
        topicArray(parseResponse(get("/topics/show.json", mapOf("id" to topicId.toString()))))
            .optJSONObject(0) ?: JSONObject()

    /** 主题回复。 */
    fun replies(topicId: Int): JSONArray = topicArray(get("/replies/show.json", mapOf("topic_id" to topicId.toString())))

    /** 节点信息。 */
    fun node(name: String): JSONObject = single(get("/nodes/show.json", mapOf("name" to name)))

    /** 用户信息。 */
    fun member(username: String): JSONObject = single(get("/members/show.json", mapOf("username" to username)))

    private fun topicArray(json: Any): JSONArray = toTopicArray(json)

    private fun single(body: String): JSONObject = (parseResponse(body) as? JSONObject) ?: JSONObject()

    private fun get(path: String, query: Map<String, String> = emptyMap()): String {
        val url = buildString {
            append(baseUrl).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
            }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("V2EX API HTTP $code: $body")
        }
        return body
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.v2ex.com/api"

        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时；V2EX 公开接口按 IP 限流，可能较慢。 */
        const val READ_TIMEOUT_MS: Int = 30_000
    }
}
