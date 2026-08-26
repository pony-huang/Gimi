package github.ponyhuang.gimi.plugin.spotify

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify Web API 客户端（https://api.spotify.com/v1）— HttpURLConnection + org.json，零第三方依赖。
 *
 * 每次请求前经 [SpotifyAuth.requireAccessToken] 取 token（过期自动刷新）；
 * 2xx 空响应（如播放控制的 204）返回 null。
 */
internal class SpotifyApi(
    private val tokenStore: TokenStore,
    private val auth: SpotifyAuth,
) {
    private val baseUrl = "https://api.spotify.com/v1"

    fun get(path: String, query: Map<String, Any?> = emptyMap()): JSONObject? =
        request("GET", path, query, null)

    fun post(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
        request("POST", path, query, body)

    fun put(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
        request("PUT", path, query, body)

    fun delete(path: String, query: Map<String, Any?> = emptyMap(), body: JSONObject? = null): JSONObject? =
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

    private fun request(
        method: String,
        path: String,
        query: Map<String, Any?>,
        body: JSONObject?,
    ): JSONObject? {
        val token = auth.requireAccessToken()
        val url = buildString {
            append(baseUrl).append(path)
            // 丢弃 null 与空字符串值（如未指定的 device_id），避免发多余的 `device_id=` 参数。
            val present = query.filterValues { it != null && (it !is String || it.isNotEmpty()) }
            if (present.isNotEmpty()) {
                append('?')
                append(present.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value.toString())}" })
            }
        }
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
        connection.disconnect()
        if (code !in 200..299) {
            throw SpotifyApiException(code, text)
        }
        return if (text.isBlank()) null else JSONObject(text)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时（如播放控制的空响应也很快返回）。 */
        const val READ_TIMEOUT_MS: Int = 30_000

        /** 把播放转移到目标设备后的等待时间，等 Spotify 完成设备切换。 */
        const val DEVICE_TRANSFER_SETTLE_MS: Long = 600L
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
        403 -> if (detail.contains("forbidden", ignoreCase = true)) {
            "$detail Spotify denied this content or operation. " +
                "Use spotify_get_my_playlists for accessible playlists, or spotify_get_top_tracks for personal popular tracks."
        } else {
            detail
        }
        404 -> "$detail This Spotify Web API endpoint or content is unavailable. " +
            "Do not retry the same request; use a currently supported personal-library or search tool instead."
        else -> detail
    }
}
