package github.ponyhuang.gimi.plugin.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 单次请求的开放平台凭证；禁止将实例或私钥写入日志。 */
internal class NeteaseCredentials(val appId: String, val privateKey: String, val clientSecret: String = "") {
    fun requireConfigured() {
        require(appId.isNotBlank() && privateKey.isNotBlank()) { "请先配置网易云 App ID 和 PKCS#8 私钥" }
    }
}

/** 仅携带安全错误代码，避免 HTTP 响应回显签名、令牌和私钥。 */
internal class NeteaseApiException(val code: Int, message: String, val http: Boolean = false) : IOException(message)

/** 开放平台签名和 HTTP 边界；不自动重试写请求、不跟随携带凭证的重定向。 */
internal class NeteaseApi(
    private val baseUrl: String = "https://openncm.music.163.com",
    private val clock: () -> Long = System::currentTimeMillis,
    ipLookupUrls: List<String>? = null,
) {
    private val publicIp = if (ipLookupUrls == null) {
        NeteasePublicIp({ transport(URL(it), null, 5_000) }, clock)
    } else {
        NeteasePublicIp({ transport(URL(it), null, 5_000) }, clock, ipLookupUrls)
    }

    // 插件由父优先 DexClassLoader 加载，不能调用可能被宿主 R8 裁剪/改写的 OkHttp ABI。
    // 使用 Android 平台 HTTP 实现，将传输边界限制在稳定的系统 API。
    suspend fun request(
        credentials: NeteaseCredentials,
        device: JSONObject,
        path: String,
        biz: JSONObject = JSONObject(),
        token: String = "",
        post: Boolean = false,
    ): Any {
        credentials.requireConfigured()
        val requestDevice = JSONObject(device.toString())
        if (requestDevice.optString("clientIp").isBlank()) requestDevice.put("clientIp", publicIp.resolve())
        val params = linkedMapOf(
            "appId" to credentials.appId,
            "signType" to "RSA_SHA256",
            "timestamp" to clock().toString(),
            "device" to requestDevice.toString(),
            "bizContent" to biz.toString(),
        )
        if (token.isNotBlank()) params["accessToken"] = token
        params["sign"] = sign(params, credentials.privateKey)
        val body = execute(path, params, post)
        val envelope = try {
            JSONObject(body)
        } catch (_: JSONException) {
            throw NeteaseApiException(0, "网易云返回无效 JSON")
        }
        val code = envelope.optInt("code", 0)
        if (code != 200) {
            val message = when (code) {
                301 -> "当前 App ID 未获此接口授权，请联系开放平台开通权限"
                2511 -> "平台未接受设备公网 IP，请稍后重试或检查网络"
                1406 -> "歌曲暂无版权"
                -444, -446, -447, -461 -> "歌曲需要相应 VIP 权限"
                else -> "网易云 API 错误（$code），请检查凭证或重新登录"
            }
            throw NeteaseApiException(code, message)
        }
        return envelope.opt("data") ?: JSONObject.NULL
    }

    private suspend fun execute(path: String, params: Map<String, String>, post: Boolean): String {
        val query = if (post) "" else params.entries.joinToString("&", prefix = "?") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return transport(URL(baseUrl.trimEnd('/') + path + query), if (post) JSONObject(params as Map<*, *>).toString() else null)
    }

    private suspend fun transport(url: URL, postBody: String?, timeout: Int = 30_000): String =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(40_000) {
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = minOf(10_000, timeout)
                connection.readTimeout = timeout
                connection.requestMethod = if (postBody != null) "POST" else "GET"
                connection.setRequestProperty("User-Agent", "ncm-0.1.7")
                connection.setRequestProperty("Referer", "https://music.163.com/")
                suspendCancellableCoroutine { continuation ->
                    // 取消不等待网络响应；断开连接使阻塞中的读写结束。
                    continuation.invokeOnCancellation { connection.disconnect() }
                    try {
                        if (postBody != null) {
                            val body = postBody.toByteArray(Charsets.UTF_8)
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            // 流式请求体不可重放，禁止平台客户端在写入后自动重复提交。
                            connection.setFixedLengthStreamingMode(body.size)
                            connection.outputStream.use { it.write(body) }
                        }
                        val code = connection.responseCode
                        if (code !in 200..299) throw NeteaseApiException(code, "网易云 HTTP 错误（$code）", http = true)
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (failure: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (failure is NeteaseApiException) failure else NeteaseNetworkException(when (failure) {
                                is java.net.UnknownHostException -> NeteaseNetworkFailure.Dns
                                is javax.net.ssl.SSLException -> NeteaseNetworkFailure.Tls
                                is java.net.SocketTimeoutException -> NeteaseNetworkFailure.Timeout
                                else -> NeteaseNetworkFailure.Connection
                            }),
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
            } ?: throw NeteaseNetworkException(NeteaseNetworkFailure.Timeout)
        }

    companion object {
        fun canonical(params: Map<String, String?>): String = params.entries
            .filter { it.key != "sign" && !it.value.isNullOrEmpty() }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }

        fun sign(params: Map<String, String?>, privateKey: String): String {
            val normalized = privateKey.replace("\\n", "\n").trim()
            val encoded = when {
                normalized.startsWith("-----BEGIN PRIVATE KEY-----") && normalized.endsWith("-----END PRIVATE KEY-----") ->
                    normalized.removePrefix("-----BEGIN PRIVATE KEY-----").removeSuffix("-----END PRIVATE KEY-----")
                !normalized.contains("-----") -> normalized
                else -> throw IllegalArgumentException("私钥必须是 PKCS#8 PEM 或 Base64 文本，不能填写文件路径")
            }.filterNot(Char::isWhitespace)
            try {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
                return Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withRSA").run {
                    initSign(key)
                    update(canonical(params).toByteArray(Charsets.UTF_8))
                    sign()
                })
            } catch (_: java.security.GeneralSecurityException) {
                throw IllegalArgumentException("无法读取 RSA PKCS#8 私钥")
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("无法读取 RSA PKCS#8 私钥")
            }
        }
    }
}
