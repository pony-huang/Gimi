package github.ponyhuang.gimi.plugin.netease

import android.content.Context
import android.os.Build
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import org.json.JSONObject
import java.util.UUID

/**
 * 登录会话只在插件内部流转，禁止序列化到工具结果。
 * @property accessToken 请求使用的登录令牌；空串表示未登录。
 * @property refreshToken 平台轮换令牌；空串表示不可刷新。
 * @property expiresAt 到期的 Unix 毫秒时间戳；0 表示平台未提供有效期。
 * @property anonymous 是否匿名会话；匿名会话不能读取账号数据或写入歌单。
 */
internal data class NeteaseSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
    val anonymous: Boolean = false,
)

/** 会话存储边界，测试不访问 Android Keystore 或真实凭证。 */
internal interface NeteaseSessionStore {
    val device: JSONObject
    fun read(identity: String): NeteaseSession
    fun write(identity: String, session: NeteaseSession)
    fun clear()
}

/** 复用安全模块加密令牌；设备 ID 在本地生成，不收集公网 IP 或硬件标识。 */
internal class AndroidNeteaseSessionStore(context: Context) : NeteaseSessionStore {
    private val prefs = context.getSharedPreferences("netease_plugin_session", Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher("gimi_netease_plugin_tokens")
    private val deviceId = prefs.getString("device_id", null) ?: ("ncmcli_" + UUID.randomUUID()).also {
        check(prefs.edit().putString("device_id", it).commit()) { "无法保存网易云设备 ID" }
    }
    override val device: JSONObject
        get() = JSONObject().put("deviceType", "openapi").put("os", "ncmcli")
            .put("appVer", "0.1.7").put("channel", "ncmcli").put("model", "Android_cli")
            .put("brand", "ncmcli").put("osVer", Build.VERSION.RELEASE).put("clientIp", "").put("deviceId", deviceId)

    override fun read(identity: String): NeteaseSession {
        if (prefs.getString("identity", null) != identity) return NeteaseSession()
        val payload = prefs.getString("tokens", null)?.let(cipher::decryptHealing) ?: return NeteaseSession()
        val data = JSONObject(payload)
        return NeteaseSession(data.getString("accessToken"), data.optString("refreshToken"), data.optLong("expiresAt"), data.optBoolean("anonymous"))
    }

    override fun write(identity: String, session: NeteaseSession) {
        val payload = JSONObject().put("accessToken", session.accessToken).put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAt).put("anonymous", session.anonymous).toString()
        check(prefs.edit().putString("identity", identity).putString("tokens", cipher.encryptOrThrow(payload)).commit()) {
            "无法保存网易云登录状态"
        }
    }

    override fun clear() {
        check(prefs.edit().remove("identity").remove("tokens").commit()) { "无法清除网易云登录状态" }
    }
}
