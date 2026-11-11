package github.ponyhuang.gimi.plugin.v2ex

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * V2EX API 2.0（Beta）客户端 — 所有接口经 Personal Access Token 认证。
 *
 * 基址 [baseUrl] 默认 [DEFAULT_BASE_URL]（`/api/v2` 前缀），可经插件配置覆盖为镜像。
 * 响应包裹 `{success, message, result}` 由 [parseEnvelope] 校验，调用方再取 result。
 * HTTP 用 JDK 自带 HttpURLConnection，JSON 用 Android 自带 org.json，插件零第三方依赖。
 */
internal class V2exApi(
    baseUrl: String = DEFAULT_BASE_URL,
    token: String = "",
) {

    /** 配置后由 [V2exPlugin.configure] 写入；工具每次执行读取当前值。 */
    @Volatile var baseUrl: String = baseUrl
    @Volatile var token: String = token

    /** 最新提醒。 */
    fun notifications(page: Int): JSONArray =
        resultArray(request("/notifications", "GET", query = mapOf("p" to page.toString())))

    /** 删除指定提醒。 */
    fun deleteNotification(notificationId: Long): JSONObject =
        envelope(request("/notifications/$notificationId", "DELETE"))

    /** 自己的 Profile。 */
    fun member(): JSONObject = resultObject(request("/member", "GET"))

    /** 当前使用的令牌。 */
    fun tokenInfo(): JSONObject = resultObject(request("/token", "GET"))

    /** 创建新令牌（最多 10 个；regular scope 不能继续建令牌）。 */
    fun createToken(scope: String, expiration: Long): JSONObject =
        envelope(
            request(
                "/tokens",
                "POST",
                body = JSONObject().put("scope", scope).put("expiration", expiration).toString(),
            ),
        )

    /** 指定节点。 */
    fun node(nodeName: String): JSONObject = resultObject(request("/nodes/${encPath(nodeName)}", "GET"))

    /** 指定节点下的主题（分页）。 */
    fun nodeTopics(nodeName: String, page: Int): JSONArray =
        resultArray(request("/nodes/${encPath(nodeName)}/topics", "GET", query = mapOf("p" to page.toString())))

    /** 指定主题详情。 */
    fun topic(topicId: Long): JSONObject = resultObject(request("/topics/$topicId", "GET"))

    /** 指定主题下的回复（分页）。 */
    fun replies(topicId: Long, page: Int): JSONArray =
        resultArray(request("/topics/$topicId/replies", "GET", query = mapOf("p" to page.toString())))

    /** 置顶自己的主题。 */
    fun setSticky(topicId: Long, duration: String): JSONObject =
        envelope(request("/topics/$topicId/set-sticky", "POST", query = mapOf("duration" to duration)))

    /** 放置自己的主题到首页（需较高等级/持有量，费用 100 铜币起）。 */
    fun boost(topicId: Long): JSONObject = envelope(request("/topics/$topicId/boost", "POST"))

    private fun resultArray(body: String): JSONArray = toTopicArray(envelope(body).opt("result"))

    private fun resultObject(body: String): JSONObject = (envelope(body).opt("result") as? JSONObject) ?: JSONObject()

    private fun envelope(body: String): JSONObject = parseEnvelope(body)

    private fun request(path: String, method: String, query: Map<String, String> = emptyMap(), body: String? = null): String {
        val url = buildString {
            append(baseUrl).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
            }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            // 禁止自动跟随 3xx：跟随会把 Authorization: Bearer 带到任意重定向目标。
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            // 响应体限长读入：异常大的响应不能把整包拉进内存。
            val responseBody = stream?.use { it.readBoundedText(MAX_RESPONSE_BYTES) }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("V2EX API HTTP $code: $responseBody")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** path 段编码：LLM 可控的节点名不得携带 / ? # 等改变请求目标的字符。 */
    private fun encPath(segment: String): String = enc(segment).replace("+", "%20")

    private fun InputStream.readBoundedText(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IllegalStateException("V2EX API 响应超过 $maxBytes 字节上限")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.v2ex.com/api/v2"

        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时；V2EX 按 IP 限流（600 次/小时），可能较慢。 */
        const val READ_TIMEOUT_MS: Int = 30_000

        /** 响应体大小上限（字节）。 */
        const val MAX_RESPONSE_BYTES: Int = 4 * 1024 * 1024
    }
}
