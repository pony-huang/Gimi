package github.ponyhuang.gimi.plugin.spotify

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.min
import kotlin.random.Random

/**
 * Spotify Web API 客户端（https://api.spotify.com/v1）— HttpURLConnection + org.json，零第三方依赖。
 *
 * 每次请求前经 [SpotifyAuth.requireAccessToken] 取 token（过期自动刷新）；
 * 2xx 空响应（如播放控制的 204）返回 null。
 *
 * 错误重试策略：
 * - 401（token 被吊销/失效）→ 强制刷新一次后重试；
 * - 429（限流）→ 按 Retry-After 头退避，指数回退封顶，最多 [MAX_RATE_LIMIT_ATTEMPTS] 次；
 * - 5xx → 不重试，直接抛给上层（Spotify 不保证一致语义）。
 */
internal class SpotifyApi(
    private val tokenStore: TokenStore,
    private val auth: SpotifyAuth,
) {
    private val baseUrl = "https://api.spotify.com/v1"

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JSONObject? =
        request("GET", path, query, null)

    suspend fun post(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
        request("POST", path, query, body)

    suspend fun put(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
        request("PUT", path, query, body)

    suspend fun delete(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
        request("DELETE", path, query, body)

    /**
     * 确保有一个可用的播放设备：优先指定设备，其次已在活动的设备，否则把播放转移到第一个设备
     * （参考 spotify-mcp-server 的 ensureActiveDevice）。返回 device_id。
     */
    suspend fun ensureActiveDevice(preferred: String?): String {
        val devices = get("/me/player/devices")?.optJSONArray("devices") ?: JSONArray()
        if (devices.length() == 0) {
            throw IllegalStateException("No Spotify device found, open Spotify on a device first")
        }
        preferred?.let { preferredId ->
            for (i in 0 until devices.length()) {
                val device = devices.optJSONObject(i)
                if (device?.optString("id") == preferredId) {
                    if (!device.optBoolean("is_active", false)) {
                        put(
                            "/me/player",
                            body = JSONObject()
                                .put("device_ids", JSONArray().put(preferredId))
                                .put("play", false),
                        )
                        delay(DEVICE_TRANSFER_SETTLE_MS)
                    }
                    return preferredId
                }
            }
        }
        for (i in 0 until devices.length()) {
            val device = devices.optJSONObject(i)
            if (device?.optBoolean("is_active", false) == true) return device.optString("id")
        }
        val target = devices.optJSONObject(0)?.optString("id")
            ?: throw IllegalStateException("No available Spotify device")
        put(
            "/me/player",
            body = JSONObject()
                .put("device_ids", JSONArray().put(target))
                .put("play", false),
        )
        delay(DEVICE_TRANSFER_SETTLE_MS)
        return target
    }

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, Any?>,
        body: JSONObject?,
    ): JSONObject? {
        val url = buildUrl(path, query)
        // 401 重试只有一次：第一次拿到 token 用旧的，发起请求，若服务端说失效则强制刷新后重试。
        // 429 重试按 Retry-After 退避，最多 MAX_RATE_LIMIT_ATTEMPTS 次。
        var attempt = 0
        var retriedAfterRefresh = false
        var retriedAfterRateLimit = 0
        while (true) {
            val token = if (attempt == 0) {
                auth.requireAccessToken()
            } else {
                // 重试期间都直接读 store，不走 expiry 缓冲判断。
                tokenStore.accessToken
                    ?: throw IllegalStateException("Spotify is not authorized, run spotify_login first")
            }
            val response = executeRequest(url, method, token, body)
            when (response.statusCode) {
                in 200..299 -> return response.toResult()
                401 -> if (!retriedAfterRefresh && !tokenStore.refreshToken.isNullOrBlank()) {
                    retriedAfterRefresh = true
                    attempt++
                    auth.forceRefresh()
                    continue
                } else {
                    throw SpotifyApiException(response.statusCode, response.text)
                }
                429 -> {
                    if (retriedAfterRateLimit >= MAX_RATE_LIMIT_ATTEMPTS) {
                        throw SpotifyApiException(response.statusCode, response.text)
                    }
                    retriedAfterRateLimit++
                    attempt++
                    delay(response.retryAfterMs ?: defaultBackoffMs(retriedAfterRateLimit))
                    continue
                }
                else -> throw SpotifyApiException(response.statusCode, response.text)
            }
        }
    }

    private fun buildUrl(path: String, query: Map<String, Any?>): String = buildString {
        append(baseUrl).append(path)
        // 丢弃 null 与空字符串值（如未指定的 device_id），避免发多余的 `device_id=` 参数。
        val present = query.filterValues { it != null && (it !is String || it.isNotEmpty()) }
        if (present.isNotEmpty()) {
            append('?')
            append(present.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value.toString())}" })
        }
    }

    private fun executeRequest(
        url: String,
        method: String,
        token: String,
        body: JSONObject?,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val text = if (code in 200..299) {
            connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        val retryAfterMs = parseRetryAfter(connection.getHeaderField("Retry-After"))
        connection.disconnect()
        return HttpResponse(code, text, retryAfterMs)
    }

    private fun parseRetryAfter(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        // Spotify 文档：Retry-After 是非负整数秒。
        val seconds = header.trim().toLongOrNull() ?: return null
        return (seconds.coerceAtLeast(0L)) * 1000L
    }

    private fun defaultBackoffMs(attempt: Int): Long {
        // 指数退避：500ms、1s、2s、4s...封顶到 8s，加 ±20% 抖动避雷。
        val base = min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS shl (attempt - 1).coerceAtLeast(0))
        val jitter = (base * 0.2).toLong()
        return base + Random.nextLong(-jitter, jitter + 1)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** 单次 HTTP 调用的原始结果。 */
    private data class HttpResponse(
        val statusCode: Int,
        val text: String,
        val retryAfterMs: Long?,
    ) {
        /** 2xx 的成功结果：空响应返回 null（如播放控制的 204）。 */
        fun toResult(): JSONObject? = if (text.isBlank()) null else JSONObject(text)
    }

    companion object {
        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时（如播放控制的空响应也很快返回）。 */
        const val READ_TIMEOUT_MS: Int = 30_000

        /** 把播放转移到目标设备后的等待时间，等 Spotify 完成设备切换。 */
        const val DEVICE_TRANSFER_SETTLE_MS: Long = 600L

        /** 429 触发后的最大重试次数（首请求之外还能再发 MAX_RATE_LIMIT_ATTEMPTS 次）。 */
        const val MAX_RATE_LIMIT_ATTEMPTS: Int = 3

        /** 指数退避的初始与封顶值（毫秒）。 */
        const val INITIAL_BACKOFF_MS: Long = 500L
        const val MAX_BACKOFF_MS: Long = 8_000L
    }
}

/** Spotify API 错误：含 HTTP 状态与响应体（便于映射成对模型友好的错误消息）。 */
internal class SpotifyApiException(val statusCode: Int, val responseBody: String) :
    Exception(spotifyErrorMessage(statusCode, responseBody))

/** 从 Spotify 错误响应体提取人类可读消息；无法解析时退回状态码。 */
internal fun spotifyErrorMessage(statusCode: Int, body: String): String {
    val message = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
    }.getOrNull()
    val detail = message ?: "Spotify API HTTP $statusCode: ${body.take(200)}"
    return when (statusCode) {
        401 -> "$detail The Spotify session is no longer valid; please run spotify_login again."
        403 -> if (detail.contains("forbidden", ignoreCase = true)) {
            "$detail Spotify denied this content or operation. " +
                "Use spotify_get_my_playlists for accessible playlists, or spotify_get_top_tracks for personal popular tracks."
        } else {
            detail
        }
        404 -> "$detail This Spotify Web API endpoint or content is unavailable. " +
            "Do not retry the same request; use a currently supported personal-library or search tool instead."
        429 -> "$detail Spotify returned HTTP 429 rate limit; the request was retried with backoff " +
            "and still failed. Wait a few seconds before retrying."
        else -> detail
    }
}
