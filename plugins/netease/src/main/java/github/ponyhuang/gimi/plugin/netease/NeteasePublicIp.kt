package github.ponyhuang.gimi.plugin.netease

import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

/** 短期缓存 CLI 协议必需的公网 IP，供应方响应只接受数字地址，不接受主机名或任意文本。 */
internal class NeteasePublicIp(
    private val lookup: suspend (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val endpoints: List<String> = listOf("https://api.ipify.org", "https://api.ip.sb/jsonip", "https://ifconfig.me/ip"),
) {
    private val mutex = Mutex()
    private var cached = ""
    private var cachedAt = 0L

    suspend fun resolve(): String = mutex.withLock {
        if (cached.isNotEmpty() && clock() - cachedAt in 0 until 300_000L) return@withLock cached
        for (endpoint in endpoints) {
            try {
                val body = lookup(endpoint).trim()
                val candidate = if (body.startsWith("{")) JSONObject(body).optString("ip") else body
                if (valid(candidate)) {
                    cached = candidate
                    cachedAt = clock()
                    return@withLock candidate
                }
            } catch (_: IOException) {
                // 供应方故障仅尝试下一个已知 IP 服务，不重放网易云业务请求。
            } catch (_: JSONException) {
                // 非 IP 响应不能进入已签名的 device 参数。
            }
        }
        throw NeteaseNetworkException(NeteaseNetworkFailure.PublicIp)
    }

    private fun valid(value: String): Boolean {
        if (value.length !in 3..45) return false
        if (value.contains(':')) {
            if (!value.all { it in "0123456789abcdefABCDEF:." }) return false
            return try { InetAddress.getByName(value).address.size in listOf(4, 16) } catch (_: java.net.UnknownHostException) { false }
        }
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
        }
    }
}

/** 网络失败分类，不携带请求 URL、签名或供应方响应原文。 */
internal enum class NeteaseNetworkFailure { Dns, Tls, Timeout, Connection, PublicIp }

/** 对配置页和工具暴露可安全本地化的网络失败原因。 */
internal class NeteaseNetworkException(val failure: NeteaseNetworkFailure) : IOException(failure.name)
