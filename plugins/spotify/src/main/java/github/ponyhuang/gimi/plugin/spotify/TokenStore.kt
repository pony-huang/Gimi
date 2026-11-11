package github.ponyhuang.gimi.plugin.spotify

import android.content.Context
import androidx.core.content.edit
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher

/**
 * Spotify token 持久化（插件私有 SharedPreferences，与宿主的 PluginConfigStore 分离；
 * PKCE 流程不再需要 Client Secret，token 是 OAuth 流程产物，走这里）。
 *
 * access/refresh token 经 AndroidKeyStore AES/GCM 加密后落盘（与宿主安全存储同一
 * 载荷格式与失效自愈策略）；过期时间戳非机密，明文存储供重启后兜底判断。
 */
internal class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    private fun readSecret(key: String): String? =
        prefs.getString(key, null)?.let(cipher::decryptHealing)

    private fun writeSecret(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit { remove(key) }
        } else {
            prefs.edit { putString(key, cipher.encryptOrThrow(value)) }
        }
    }

    var accessToken: String?
        get() = readSecret(KEY_ACCESS)
        set(value) {
            writeSecret(KEY_ACCESS, value)
        }

    var refreshToken: String?
        get() = readSecret(KEY_REFRESH)
        set(value) {
            writeSecret(KEY_REFRESH, value)
        }

    /** access token 过期时刻（挂钟毫秒时间戳）；重启后兜底判断。 */
    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) {
            prefs.edit { putLong(KEY_EXPIRES_AT, value) }
        }

    /** token 写入时刻（SystemClock.elapsedRealtime 毫秒）；单调时钟判定，不受改时间影响。 */
    var storedAtElapsed: Long
        get() = prefs.getLong(KEY_STORED_AT_ELAPSED, 0L)
        set(value) {
            prefs.edit { putLong(KEY_STORED_AT_ELAPSED, value) }
        }

    /** 有效期秒数（与 [storedAtElapsed] 配套）。 */
    var expiresInSeconds: Long
        get() = prefs.getLong(KEY_EXPIRES_IN_SECONDS, 0L)
        set(value) {
            prefs.edit { putLong(KEY_EXPIRES_IN_SECONDS, value) }
        }

    /** 与 [SpotifyAuth.requireAccessToken] 口径一致：access 缺失但有 refresh 时可刷新恢复。 */
    val isAuthorized: Boolean
        get() = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_NAME = "spotify_plugin_tokens"
        const val KEY_ALIAS = "gimi_spotify_plugin_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_STORED_AT_ELAPSED = "stored_at_elapsed"
        const val KEY_EXPIRES_IN_SECONDS = "expires_in_seconds"
    }
}
