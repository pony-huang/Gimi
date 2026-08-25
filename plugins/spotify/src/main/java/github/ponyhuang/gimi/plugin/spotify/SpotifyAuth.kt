package github.ponyhuang.gimi.plugin.spotify

import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

/**
 * Spotify OAuth 2.0 Authorization Code 流程：
 * 拼授权 URL → 本地 127.0.0.1 回调端口接住 code → 换 access/refresh token → 过期自动刷新。
 *
 * 回调用 JDK [ServerSocket] 手写的最小 HTTP server（只服务单次 GET），零第三方依赖。
 * redirect_uri 须是 `http://127.0.0.1:<port>/callback`（官方支持 localhost 回调），
 * 并在 Spotify Developer Dashboard 的 App Redirect URIs 里注册。
 */
internal class SpotifyAuth(
    private val tokenStore: TokenStore,
    private val credentials: () -> Pair<String, String>,
) {
    private val accountsBase = "https://accounts.spotify.com"

    /** 覆盖本插件全部工具所需 scope 的授权 URL。 */
    fun authorizeUrl(clientId: String, redirectUri: String, state: String): String =
        buildAuthorizeUrl(clientId, redirectUri, state, SCOPES)

    /** 用授权码换 token 并持久化（Basic 认证）。 */
    fun exchangeCode(code: String, redirectUri: String) {
        val (clientId, clientSecret) = credentials()
        val body = "grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(redirectUri)}"
        val bundle = tokenRequest(clientId, clientSecret, body)
        saveTokens(bundle)
    }

    /**
     * 当前可用的 access token；过期则自动刷新；未授权抛异常。
     * 刷新返回 invalid_grant（refresh token 已失效）时清空并提示重新登录。
     */
    fun requireAccessToken(): String {
        val access = tokenStore.accessToken
        if (access.isNullOrBlank()) {
            throw IllegalStateException("Spotify is not authorized, run spotify_login first")
        }
        val refresh = tokenStore.refreshToken
        if (refresh.isNullOrBlank() || !needsRefresh(tokenStore.expiresAt, System.currentTimeMillis(), REFRESH_BUFFER_MS)) {
            return access
        }
        val (clientId, clientSecret) = credentials()
        return try {
            val body = "grant_type=refresh_token&refresh_token=${enc(refresh)}"
            val bundle = tokenRequest(clientId, clientSecret, body)
            saveTokens(bundle)
            tokenStore.accessToken ?: throw SpotifyAuthException("Refresh failed: no access token returned")
        } catch (e: SpotifyAuthException) {
            if (e.isInvalidGrant) {
                tokenStore.clear()
                throw IllegalStateException("Spotify login expired, please authorize again")
            }
            throw e
        }
    }

    /** 撤销本地 token（不调用服务端登出）。 */
    fun clearTokens() {
        tokenStore.clear()
    }

    /** 是否已持有可用凭据（access 或 refresh token）。 */
    fun hasAccessToken(): Boolean = tokenStore.isAuthorized

    private fun tokenRequest(clientId: String, clientSecret: String, formBody: String): TokenBundle {
        val connection = URL("$accountsBase/api/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Basic ${base64("$clientId:$clientSecret")}")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val text = if (code in 200..299) {
            connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (code !in 200..299) {
            val invalidGrant = text.contains("\"invalid_grant\"")
            throw SpotifyAuthException("Spotify token request failed HTTP $code: ${text.take(200)}", invalidGrant)
        }
        return parseTokenResponse(JSONObject(text))
    }

    private fun saveTokens(bundle: TokenBundle) {
        tokenStore.accessToken = bundle.accessToken
        // 刷新时 Spotify 可能轮换 refresh token，新值写回（不存在则不覆盖）。
        if (bundle.refreshToken != null) {
            tokenStore.refreshToken = bundle.refreshToken
        }
        tokenStore.expiresAt = System.currentTimeMillis() + bundle.expiresIn * 1000
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    companion object {
        /** 提前刷新的余量：access token 有效期 1 小时，提前 5 分钟刷新留足缓冲。 */
        const val REFRESH_BUFFER_MS: Long = 5 * 60 * 1000L

        /** 登录等待回调超时。 */
        const val LOGIN_TIMEOUT_MS: Long = 180_000L

        /** 回调成功/失败页面的 HTML。 */
        const val SUCCESS_HTML: String =
            "<html><body style='font-family:sans-serif;text-align:center;padding-top:40px'>" +
                "<h2>Spotify sign-in succeeded</h2><p>You can close this page and return to the app.</p></body></html>"
        const val FAILURE_HTML: String =
            "<html><body style='font-family:sans-serif;text-align:center;padding-top:40px'>" +
                "<h2>Spotify sign-in failed</h2><p>Please close this page and retry spotify_login.</p></body></html>"

        val SCOPES: List<String> = listOf(
            "user-read-private",
            "user-read-email",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-read-playback-position",
            "playlist-read-private",
            "playlist-read-collaborative",
            "playlist-modify-private",
            "playlist-modify-public",
            "user-library-read",
            "user-library-modify",
            "user-read-recently-played",
            "user-top-read",
        )

        /** 生成 OAuth state（防 CSRF）。 */
        fun newState(): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { alphabet[(it.toInt() and 0xFF) % alphabet.length].toString() }
        }
    }
}

/**
 * 起本地回调服务器并返回句柄。先绑定端口，调用方随后开浏览器授权，
 * 再 [CallbackServer.await] 阻塞等回调。超时由 ServerSocket.soTimeout 触发。
 */
internal fun startCallbackServer(redirectUri: String, expectedState: String): CallbackServer {
    val target = parseRedirectUri(redirectUri)
    val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress("127.0.0.1", target.port))
        soTimeout = SpotifyAuth.LOGIN_TIMEOUT_MS.toInt()
    }
    return CallbackServer(server, target.path, expectedState)
}

/** 本地回调服务器句柄：绑定后由调用方开浏览器，再 [await] 阻塞等回调。 */
internal class CallbackServer(
    private val server: ServerSocket,
    private val path: String,
    private val state: String,
) {
    /**
     * 阻塞等待浏览器回调；成功返回授权码，失败/超时抛 [SpotifyAuthException]。
     *
     * 读请求行时不能 `.use {}` 关闭 reader——那会连带关闭输入流与 socket，
     * 导致后续写响应失败（浏览器报「无法访问页面」）。socket 由结尾统一 [java.net.Socket.close]。
     */
    fun await(): String {
        try {
            val socket = server.accept()
            val requestLine = socket.getInputStream().bufferedReader().readLine() ?: ""
            val result = parseRedirectRequest(requestLine, path, state)
            val html = if (result is RedirectResult.Success) SpotifyAuth.SUCCESS_HTML else SpotifyAuth.FAILURE_HTML
            writeResponse(socket.getOutputStream(), html)
            socket.close()
            return when (result) {
                is RedirectResult.Success -> result.code
                is RedirectResult.Error -> throw SpotifyAuthException(result.message)
            }
        } catch (e: SocketTimeoutException) {
            throw SpotifyAuthException("Authorization timed out, please try again")
        }
    }

    fun close() {
        runCatching { server.close() }
    }

    private fun writeResponse(output: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}

/** 拼授权 URL（纯函数，便于单测）。 */
internal fun buildAuthorizeUrl(
    clientId: String,
    redirectUri: String,
    state: String,
    scopes: List<String>,
): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "response_type" to "code",
        "redirect_uri" to redirectUri,
        "scope" to scopes.joinToString(" "),
        "state" to state,
        "show_dialog" to "true",
    )
    return "https://accounts.spotify.com/authorize?" +
        params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
}

/** access token 是否需要在 [now] 时刻刷新（距过期不足 [bufferMs]）。 */
internal fun needsRefresh(expiresAt: Long, now: Long, bufferMs: Long): Boolean =
    now + bufferMs >= expiresAt

/** OAuth token 交换响应解析结果。 */
internal data class TokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)

internal fun parseTokenResponse(json: JSONObject): TokenBundle = TokenBundle(
    accessToken = json.optString("access_token"),
    refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
    expiresIn = json.optLong("expires_in", 3600),
)

/** 回调 redirect_uri 解析出的本地监听目标。 */
internal data class RedirectTarget(val port: Int, val path: String)

internal fun parseRedirectUri(uri: String): RedirectTarget {
    val parsed = URI(uri)
    if (parsed.scheme !in setOf("http", "https") || parsed.host !in setOf("localhost", "127.0.0.1")) {
        throw SpotifyAuthException("redirect_uri must be http://127.0.0.1:<port>/callback")
    }
    val port = if (parsed.port == -1) 80 else parsed.port
    val path = parsed.path?.takeIf { it.isNotBlank() } ?: "/"
    return RedirectTarget(port, path)
}

/** 单次本地回调的解析结果。 */
internal sealed interface RedirectResult {
    data class Success(val code: String) : RedirectResult
    data class Error(val message: String) : RedirectResult
}

/** 解析浏览器回调的请求行 `GET /callback?code=..&state=.. HTTP/1.1`。 */
internal fun parseRedirectRequest(
    requestLine: String,
    expectedPath: String,
    expectedState: String,
): RedirectResult {
    // 请求行形如 "GET /callback?code=..&state=.. HTTP/1.1"，路径是第二个 token。
    val pathAndQuery = requestLine.trim().split(' ').getOrNull(1).orEmpty()
    if (!pathAndQuery.startsWith(expectedPath)) {
        return RedirectResult.Error("Unknown callback path: $pathAndQuery")
    }
    val query = pathAndQuery.substringAfter('?', "")
    if (query.isEmpty()) return RedirectResult.Error("Callback missing parameters")
    val params = query.split('&').mapNotNull { pair ->
        val kv = pair.split('=', limit = 2)
        if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
    }.toMap()
    val error = params["error"]
    if (error != null) return RedirectResult.Error("Authorization failed: $error")
    if (params["state"] != expectedState) return RedirectResult.Error("State verification failed")
    val code = params["code"]
    return if (code.isNullOrBlank()) {
        RedirectResult.Error("Callback missing code")
    } else {
        RedirectResult.Success(code)
    }
}

/** OAuth 流程错误；[isInvalidGrant] 标记 refresh token 已失效（需重新登录）。 */
internal class SpotifyAuthException(message: String, val isInvalidGrant: Boolean = false) :
    Exception(message)
