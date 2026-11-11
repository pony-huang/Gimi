package github.ponyhuang.gimi.plugin.spotify

import android.os.SystemClock
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Spotify OAuth 2.0 Authorization Code + PKCE 流程：
 * 生成 code_verifier/challenge → 拼授权 URL → 本地 127.0.0.1 回调端口接住 code →
 * 携 code_verifier 换 access/refresh token → 过期自动刷新。
 *
 * 移动端公共客户端无法保密 client secret，官方推荐 PKCE：token 请求不再携带
 * `Authorization: Basic`，也不依赖配置页的 Client Secret。
 *
 * 回调用 JDK [ServerSocket] 手写的最小 HTTP server（只服务单次 GET），零第三方依赖。
 * redirect_uri 须是 `http://127.0.0.1:<port>/callback`（官方支持 localhost 回调），
 * 并在 Spotify Developer Dashboard 的 App Redirect URIs 里注册。
 */
internal class SpotifyAuth(
    private val tokenStore: TokenStore,
    private val clientId: () -> String,
) {
    private val accountsBase = "https://accounts.spotify.com"
    private val refreshLock = Any()

    /** 覆盖本插件全部工具所需 scope 的授权 URL（PKCE S256）。 */
    fun authorizeUrl(clientId: String, redirectUri: String, state: String, codeChallenge: String): String =
        buildAuthorizeUrl(clientId, redirectUri, state, codeChallenge, SCOPES)

    /** 用授权码换 token 并持久化（PKCE，不使用 client secret）。 */
    fun exchangeCode(code: String, redirectUri: String, codeVerifier: String) {
        val body = "grant_type=authorization_code" +
            "&code=${enc(code)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&client_id=${enc(clientId())}" +
            "&code_verifier=${enc(codeVerifier)}"
        val bundle = tokenRequest(body)
        saveTokens(bundle)
    }

    /**
     * 当前可用的 access token；过期则自动刷新；未授权抛异常。
     * access 缺失而 refresh 尚存时先尝试恢复（与 [TokenStore.isAuthorized] 口径一致）。
     * 刷新返回 invalid_grant（refresh token 已失效）时清空并提示重新登录。
     */
    fun requireAccessToken(): String {
        val access = tokenStore.accessToken
        val refresh = tokenStore.refreshToken
        if (access.isNullOrBlank()) {
            if (refresh.isNullOrBlank()) {
                throw IllegalStateException("Spotify is not authorized, run spotify_login first")
            }
            // access 被清空但 refresh 可用：先刷新恢复，而不是直接报未授权。
            return refreshAndStore(refresh)
        }
        if (refresh.isNullOrBlank() || !isTokenStale(REFRESH_BUFFER_MS)) {
            return access
        }
        return refreshAndStore(refresh)
    }

    /**
     * 强制刷新一次 access token（忽略过期判断）。用于 API 返回 401/access token 被吊销的兜底；
     * 调用方需已确认持有 refresh token。返回新写入的 access token。
     */
    fun forceRefresh(): String {
        val refresh = tokenStore.refreshToken
            ?: throw IllegalStateException("Spotify login expired, please authorize again")
        return refreshAndStore(refresh)
    }

    /**
     * 刷新并写回。刷新请求无并发保护会重复提交、invalid_grant 时还会误清其它线程
     * 刚写入的新 token，因此全程持有 [refreshLock]；清空前确认 store 里的
     * refresh token 仍是本次使用的旧值（轮换后被其它线程更新过则保留）。
     */
    private fun refreshAndStore(refresh: String): String = synchronized(refreshLock) {
        try {
            val body = "grant_type=refresh_token" +
                "&refresh_token=${enc(refresh)}" +
                "&client_id=${enc(clientId())}"
            val bundle = tokenRequest(body)
            saveTokens(bundle)
            tokenStore.accessToken?.takeIf(String::isNotBlank)
                ?: throw SpotifyAuthException("Refresh failed: no access token returned")
        } catch (e: SpotifyAuthException) {
            if (e.isInvalidGrant) {
                if (tokenStore.refreshToken == refresh) {
                    tokenStore.clear()
                }
                throw IllegalStateException("Spotify login expired, please authorize again")
            }
            throw e
        }
    }

    /**
     * token 是否已进入刷新窗口。挂钟与单调时钟（elapsedRealtime）双判定：
     * 挂钟兜底进程重启（elapsed 归零），单调钟兜底用户改时间/NTP 跳变。
     */
    private fun isTokenStale(bufferMs: Long): Boolean {
        val wallExpiry = tokenStore.expiresAt
        val wallStale = wallExpiry > 0 && needsRefresh(wallExpiry, System.currentTimeMillis(), bufferMs)
        val storedAt = tokenStore.storedAtElapsed
        val seconds = tokenStore.expiresInSeconds
        val elapsedStale = storedAt > 0 && seconds > 0 &&
            SystemClock.elapsedRealtime() + bufferMs >= storedAt + seconds * 1000
        return wallStale || elapsedStale
    }

    /** 撤销本地 token（不调用服务端登出）。 */
    fun clearTokens() {
        tokenStore.clear()
    }

    /** 是否已持有可用凭据（access 或 refresh token）。 */
    fun hasAccessToken(): Boolean = tokenStore.isAuthorized

    private fun tokenRequest(formBody: String): TokenBundle {
        val connection = URL("$accountsBase/api/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        // PKCE 公共客户端：不带 Authorization: Basic（client secret 无法在端上保密）。
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val code: Int
        val text: String
        try {
            connection.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            code = connection.responseCode
            text = if (code in 200..299) {
                connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
        } finally {
            connection.disconnect()
        }
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
        // 有效期夹取到合理区间，防止响应异常把过期时刻推到极远/极近。
        val seconds = bundle.expiresIn.coerceIn(MIN_EXPIRES_SECONDS, MAX_EXPIRES_SECONDS)
        tokenStore.expiresAt = System.currentTimeMillis() + seconds * 1000
        tokenStore.expiresInSeconds = seconds
        tokenStore.storedAtElapsed = SystemClock.elapsedRealtime()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val random = SecureRandom()

        /** 提前刷新的余量：access token 有效期 1 小时，提前 5 分钟刷新留足缓冲。 */
        const val REFRESH_BUFFER_MS: Long = 5 * 60 * 1000L

        /** 登录等待回调超时。 */
        const val LOGIN_TIMEOUT_MS: Long = 180_000L

        /** token 交换/刷新请求超时。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000
        const val READ_TIMEOUT_MS: Int = 30_000

        /** expires_in 合理区间（秒）：防异常响应把过期时间推到极端。 */
        const val MIN_EXPIRES_SECONDS: Long = 60L
        const val MAX_EXPIRES_SECONDS: Long = 86_400L

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

        private val PKCE_ALPHABET: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /** 生成 OAuth state（防 CSRF）；用 SecureRandom 的有界采样，无取模偏差。 */
        fun newState(): String = randomToken(16)

        /** 生成 PKCE code_verifier（RFC 7636：43-128 个 unreserved 字符）。 */
        fun newCodeVerifier(): String = randomToken(64)

        /** code_verifier → S256 code_challenge（Base64url 无填充）。 */
        fun codeChallengeS256(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
                )

        private fun randomToken(length: Int): String {
            val buffer = CharArray(length)
            repeat(length) { index -> buffer[index] = PKCE_ALPHABET[random.nextInt(PKCE_ALPHABET.length)] }
            return String(buffer)
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
     * 导致后续写响应失败（浏览器报「无法访问页面」）。socket 在 finally 里统一关闭，
     * 且带读超时：恶意/卡死的连接不能让授权流程永久挂起。
     */
    fun await(): String {
        val socket = try {
            server.accept()
        } catch (e: SocketTimeoutException) {
            throw SpotifyAuthException("Authorization timed out, please try again")
        }
        try {
            // accept 后的连接单独设读超时：ServerSocket.soTimeout 只约束 accept()。
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            val requestLine = socket.getInputStream().bufferedReader().readLine() ?: ""
            val result = parseRedirectRequest(requestLine, path, state)
            val html = if (result is RedirectResult.Success) SpotifyAuth.SUCCESS_HTML else SpotifyAuth.FAILURE_HTML
            writeResponse(socket.getOutputStream(), html)
            return when (result) {
                is RedirectResult.Success -> result.code
                is RedirectResult.Error -> throw SpotifyAuthException(result.message)
            }
        } catch (e: SocketTimeoutException) {
            throw SpotifyAuthException("Authorization callback stalled, please try again")
        } finally {
            runCatching { socket.close() }
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

    private companion object {
        /** 回调连接读超时：请求行迟迟不来的连接直接丢弃。 */
        const val SOCKET_READ_TIMEOUT_MS: Int = 10_000
    }
}

/** 拼授权 URL（纯函数，便于单测）。 */
internal fun buildAuthorizeUrl(
    clientId: String,
    redirectUri: String,
    state: String,
    codeChallenge: String,
    scopes: List<String>,
): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "response_type" to "code",
        "redirect_uri" to redirectUri,
        "scope" to scopes.joinToString(" "),
        "state" to state,
        "code_challenge" to codeChallenge,
        "code_challenge_method" to "S256",
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
