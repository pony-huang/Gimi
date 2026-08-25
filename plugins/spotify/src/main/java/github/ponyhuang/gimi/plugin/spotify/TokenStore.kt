package github.ponyhuang.gimi.plugin.spotify

import android.content.Context

/**
 * Spotify token 持久化（插件私有 SharedPreferences，与宿主的 PluginConfigStore 分离；
 * Client ID/Secret 由用户填在配置页，token 是 OAuth 流程产物，走这里）。
 */
internal class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) {
            prefs.edit().putString(KEY_ACCESS, value).apply()
        }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) {
            prefs.edit().putString(KEY_REFRESH, value).apply()
        }

    /** access token 过期时刻（毫秒时间戳）。 */
    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()
        }

    val isAuthorized: Boolean
        get() = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "spotify_plugin_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
