package github.ponyhuang.gimi.plugin.spotify

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.plugin.spotify.tools.authTools
import github.ponyhuang.gimi.plugin.spotify.tools.libraryTools
import github.ponyhuang.gimi.plugin.spotify.tools.playbackTools
import github.ponyhuang.gimi.plugin.spotify.tools.playlistTools
import github.ponyhuang.gimi.plugin.spotify.tools.searchTools
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * Spotify 插件 — 接入官方 Web API，向 Agent 注入内容工具：
 * 搜索 / 目录查询 / 歌单 / 收藏 / 播放控制 / 榜单等。
 *
 * 鉴权走 OAuth 2.0 Authorization Code：配置页填 Client ID / Client Secret / Redirect URI
 * （默认 `http://127.0.0.1:8888/callback`，需在 Spotify Developer Dashboard 注册），
 * 对话里触发 `spotify_login` 在浏览器完成授权；播放控制需 Spotify Premium。
 */
class SpotifyPlugin : AgentPlugin {

    override val pluginId: String = "spotify"
    override val displayName: String = "Spotify"
    override val version: Int = 1
    override val name: String = "spotify_plugin"

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(key = KEY_CLIENT_ID, label = "Client ID"),
            PluginConfigField.Text(key = KEY_CLIENT_SECRET, label = "Client Secret", secret = true),
            PluginConfigField.Text(
                key = KEY_REDIRECT_URI,
                label = "Redirect URI",
                defaultValue = DEFAULT_REDIRECT_URI,
            ),
        ),
    )

    @Volatile private var clientId: String = ""
    @Volatile private var clientSecret: String = ""
    @Volatile private var redirectUri: String = DEFAULT_REDIRECT_URI
    @Volatile private var appContext: Context? = null

    override fun onAttach(context: Context) {
        appContext = context.applicationContext
    }

    override fun configure(values: Map<String, String>) {
        clientId = values[KEY_CLIENT_ID].orEmpty()
        clientSecret = values[KEY_CLIENT_SECRET].orEmpty()
        redirectUri = values[KEY_REDIRECT_URI]?.takeIf(String::isNotBlank) ?: DEFAULT_REDIRECT_URI
    }

    private val tokenStore: TokenStore by lazy {
        TokenStore(requireNotNull(appContext) { "Spotify 插件未附加上下文" })
    }
    private val auth: SpotifyAuth by lazy {
        SpotifyAuth(tokenStore) { clientId to clientSecret }
    }
    private val api: SpotifyApi by lazy {
        SpotifyApi(tokenStore, auth)
    }

    override fun tools(): List<BaseTool> = buildList {
        addAll(authTools(auth, { clientId }, { clientSecret }, { redirectUri }) { appContext })
        addAll(searchTools(api))
        addAll(libraryTools(api))
        addAll(playbackTools(api))
        addAll(playlistTools(api))
    }

    companion object {
        const val KEY_CLIENT_ID: String = "client_id"
        const val KEY_CLIENT_SECRET: String = "client_secret"
        const val KEY_REDIRECT_URI: String = "redirect_uri"
        const val DEFAULT_REDIRECT_URI: String = "http://127.0.0.1:8888/callback"
    }
}
