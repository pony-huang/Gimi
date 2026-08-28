package github.ponyhuang.gimi.plugin.spotify

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.plugin.spotify.tools.authTools
import github.ponyhuang.gimi.plugin.spotify.tools.libraryTools
import github.ponyhuang.gimi.plugin.spotify.tools.performLogin
import github.ponyhuang.gimi.plugin.spotify.tools.playbackTools
import github.ponyhuang.gimi.plugin.spotify.tools.playlistTools
import github.ponyhuang.gimi.plugin.spotify.tools.searchTools
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginActionResult
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * Spotify plugin — connects the official Web API and injects content tools into the agent:
 * search / catalog / playlists / library / playback / top lists.
 *
 * Auth uses OAuth 2.0 Authorization Code. Fill in Client ID / Client Secret / Redirect URI
 * (default `http://127.0.0.1:8888/callback`, registered in the Spotify Developer Dashboard),
 * then tap the "Authorize Spotify" config action (in-app WebView) or call the `spotify_login`
 * tool. Playback tools require Spotify Premium.
 */
class SpotifyPlugin : AgentPlugin {

    override val pluginId: String = "spotify"
    override val displayName: String = "Spotify"
    override val version: Int = 1
    override val toolCount: Int = 29

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
        actions = listOf(
            PluginConfigAction(id = ACTION_LOGIN, label = "Authorize Spotify"),
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
        TokenStore(requireNotNull(appContext) { "Spotify plugin has no attached context" })
    }
    private val auth: SpotifyAuth by lazy {
        SpotifyAuth(tokenStore) { clientId to clientSecret }
    }
    private val api: SpotifyApi by lazy {
        SpotifyApi(tokenStore, auth)
    }

    /** 工具集与工具列表均复用实例，配置值仍由各工具在调用时通过闭包读取。 */
    override fun toolSets(): List<Toolset> = toolSets

    private val toolSets: List<Toolset> by lazy { listOf(SpotifyToolset { toolList }) }

    private val toolList: List<BaseTool> by lazy {
        buildList {
            addAll(authTools(auth, { clientId }, { clientSecret }, { redirectUri }) { appContext })
            addAll(searchTools(api))
            addAll(libraryTools(api))
            addAll(playbackTools(api))
            addAll(playlistTools(api))
        }
    }

    override suspend fun runConfigAction(actionId: String): PluginActionResult = when (actionId) {
        ACTION_LOGIN -> runCatching {
            performLogin(auth, { clientId }, { clientSecret }, { redirectUri }) { appContext }
        }.fold(
            onSuccess = { message -> PluginActionResult(message = message) },
            onFailure = { error ->
                PluginActionResult(message = error.message ?: "Authorization failed", success = false)
            },
        )
        else -> PluginActionResult(message = "Unknown action: $actionId", success = false)
    }

    companion object {
        const val ACTION_LOGIN: String = "login"
        const val KEY_CLIENT_ID: String = "client_id"
        const val KEY_CLIENT_SECRET: String = "client_secret"
        const val KEY_REDIRECT_URI: String = "redirect_uri"
        const val DEFAULT_REDIRECT_URI: String = "http://127.0.0.1:8888/callback"
    }
}
