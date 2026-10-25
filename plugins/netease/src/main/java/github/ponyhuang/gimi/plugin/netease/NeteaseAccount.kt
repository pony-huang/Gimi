package github.ponyhuang.gimi.plugin.netease

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/** 串行管理扫码、令牌刷新与业务调用；凭证变化后拒绝写入旧请求的登录态。 */
internal class NeteaseAccount(
    private val api: NeteaseApi,
    private val store: NeteaseSessionStore,
    private val credentials: () -> NeteaseCredentials,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var qrKey = ""
    private var qrIdentity = ""
    private var qrExpiresAt = 0L

    private fun identity(value: NeteaseCredentials): String = MessageDigest.getInstance("SHA-256")
        .digest((value.appId + "\u0000" + value.privateKey.replace("\\n", "\n").trim()).toByteArray())
        .joinToString("") { "%02x".format(it) }

    suspend fun status(): Map<String, Any?> = mutex.withLock {
        val session = store.read(identity(credentials()))
        mapOf("authorized" to (session.accessToken.isNotBlank() && (session.expiresAt == 0L || session.expiresAt > clock())),
            "anonymous" to session.anonymous, "expires_at" to session.expiresAt)
    }

    suspend fun logout(): Map<String, Any?> = mutex.withLock {
        store.clear()
        qrKey = ""
        mapOf("success" to true)
    }

    suspend fun validate(): Map<String, Any?> = mutex.withLock {
        val c = credentials()
        api.request(c, store.device, "/openapi/music/basic/do/noting", JSONObject().put("clientId", c.appId))
        mapOf("valid" to true)
    }

    suspend fun anonymousLogin(): Map<String, Any?> = mutex.withLock {
        val c = credentials()
        anonymousLogin(c)
        mapOf("authorized" to true, "anonymous" to true)
    }

    private suspend fun anonymousLogin(c: NeteaseCredentials): NeteaseSession {
        val data = api.request(c, store.device, "/openapi/music/basic/oauth2/login/anonymous",
            JSONObject().put("clientId", c.appId), post = true) as? JSONObject
            ?: throw IllegalStateException("匿名登录未返回有效登录态")
        return save(c, data, anonymous = true)
    }

    suspend fun createQr(): Map<String, Any?> = mutex.withLock {
        val c = credentials()
        var session = store.read(identity(c))
        // CLI 先获取有效 access token 再生成二维码；首次使用自动建立匿名会话。
        if (session.accessToken.isBlank() || session.expiresAt > 0 && session.expiresAt <= clock()) {
            // 发起新的扫码授权不能依赖旧 refresh token；CLI 同样先建立可用匿名会话。
            session = anonymousLogin(c)
        }
        val data = api.request(c, store.device, "/openapi/music/basic/user/oauth2/qrcodekey/get/v2",
            JSONObject().put("type", 2).put("expiredKey", "300"), session.accessToken) as? JSONObject
            ?: throw IllegalStateException("未返回登录二维码")
        val key = data.optString("uniKey").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalStateException("未返回二维码标识")
        val url = data.optString("qrCodeUrl")
        val uri = try { URI(url) } catch (_: java.net.URISyntaxException) {
            throw IllegalStateException("网易云返回了无效的登录链接")
        }
        require(uri.scheme == "https" && uri.host in setOf("163cn.tv", "music.163.com") && uri.userInfo == null) {
            "网易云返回了无效的登录链接"
        }
        qrKey = key
        qrIdentity = identity(c)
        qrExpiresAt = clock() + 300_000
        mapOf("qr_code_url" to url, "expires_in" to 300)
    }

    suspend fun pollQr(): Map<String, Any?> = mutex.withLock {
        val c = credentials()
        require(qrKey.isNotBlank() && qrIdentity == identity(c) && clock() < qrExpiresAt) { "请先生成新的登录二维码" }
        val data = api.request(c, store.device, "/openapi/music/basic/oauth2/device/login/qrcode/get",
            JSONObject().put("key", qrKey).put("clientId", c.appId), store.read(identity(c)).accessToken) as? JSONObject
            ?: throw IllegalStateException("未返回扫码状态")
        val status = data.optInt("status")
        if (status == 803) {
            val tokens = data.optJSONObject("accessToken") ?: throw IllegalStateException("扫码成功但未返回有效登录态")
            val access = tokens.optString("accessToken").takeIf { it.isNotBlank() && it != "null" }
                ?: throw IllegalStateException("扫码成功但未返回有效登录态")
            // CLI 在扫码后读取用户资料确认账号可用；验证失败不能把匿名会话标成已授权。
            api.request(c, store.device, "/openapi/music/basic/user/profile/get/v2", token = access)
            save(c, tokens, anonymous = false)
            qrKey = ""
        } else if (status == 800) {
            qrKey = ""
        }
        // 平台的 msg 可能含用户数据；只暴露状态码和稳定解释。
        mapOf("status" to status, "authorized" to (status == 803), "message" to when (status) {
            800 -> "二维码已失效，请重新生成"
            801 -> "等待扫码"
            802 -> "已扫码，等待确认"
            803 -> "登录成功"
            else -> "请在网易云客户端确认登录，稍后再次检查"
        })
    }

    suspend fun refresh(): Map<String, Any?> = mutex.withLock {
        val c = credentials()
        refresh(c, store.read(identity(c)))
        mapOf("authorized" to true)
    }

    private suspend fun refresh(c: NeteaseCredentials, session: NeteaseSession): NeteaseSession {
        require(session.refreshToken.isNotBlank()) { "请重新扫码登录网易云" }
        val data = api.request(c, store.device, "/openapi/music/basic/user/oauth2/token/refresh/v2",
            JSONObject().put("clientId", c.appId).put("clientSecret", c.clientSecret).put("refreshToken", session.refreshToken),
            session.accessToken) as? JSONObject ?: throw IllegalStateException("刷新登录未返回有效登录态")
        return save(c, data, session.anonymous, session.refreshToken)
    }

    suspend fun business(path: String, biz: JSONObject = JSONObject(), post: Boolean = false, memberOnly: Boolean = true): Any = mutex.withLock {
        val c = credentials()
        var session = store.read(identity(c))
        require(session.accessToken.isNotBlank()) { "请先匿名登录或扫码登录网易云" }
        require(!memberOnly || !session.anonymous) { "此操作需要网易云账号登录，请先扫码登录" }
        if (session.expiresAt > 0 && session.expiresAt <= clock() + 30_000) session = refresh(c, session)
        // 不在业务错误或 401 后自动重放，避免批量加歌等写操作重复提交。
        api.request(c, store.device, path, biz, session.accessToken, post)
    }

    private fun save(c: NeteaseCredentials, data: JSONObject, anonymous: Boolean, fallbackRefresh: String = ""): NeteaseSession {
        check(identity(c) == identity(credentials())) { "配置已改变，请重新登录" }
        val access = data.optString("accessToken").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalStateException("平台未返回有效 access token")
        val seconds = data.optLong("expireTime", data.optLong("expiresTime", 0))
        require(seconds in 0..31_536_000) { "平台返回了无效的令牌有效期" }
        val refresh = data.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" } ?: fallbackRefresh
        val session = NeteaseSession(access, refresh, if (seconds > 0) clock() + seconds * 1000 else 0, anonymous)
        store.write(identity(c), session)
        return session
    }
}
