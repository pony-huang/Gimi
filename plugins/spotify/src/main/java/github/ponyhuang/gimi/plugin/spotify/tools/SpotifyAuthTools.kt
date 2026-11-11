package github.ponyhuang.gimi.plugin.spotify.tools

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyAuth
import github.ponyhuang.gimi.plugin.spotify.startCallbackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun authTools(
    auth: SpotifyAuth,
    clientId: () -> String,
    redirectUri: () -> String,
    appContext: () -> Context?,
): List<BaseTool> = listOf(
    spotifyTool(
        name = "spotify_login",
        description =
            "Open the browser to complete Spotify OAuth authorization (configure Client ID first). " +
                "Required before first use.",
        parameters = Schema(type = Type.OBJECT),
    ) {
        mapOf(
            SpotifyTool.RESULT_KEY to
                performLogin(auth, clientId, redirectUri, appContext),
        )
    },
    spotifyTool(
        name = "spotify_auth_status",
        description = "Check whether the Spotify plugin is authorized (logged in).",
        parameters = Schema(type = Type.OBJECT),
    ) {
        val authorized = auth.hasAccessToken()
        mapOf(
            SpotifyTool.RESULT_KEY to mapOf(
                "authorized" to authorized,
                "message" to if (authorized) "Authorized" else "Not authorized, run spotify_login first",
            ),
        )
    },
    spotifyTool(
        name = "spotify_logout",
        description = "Clear the local Spotify token; run spotify_login again afterwards.",
        parameters = Schema(type = Type.OBJECT),
    ) {
        auth.clearTokens()
        mapOf(SpotifyTool.RESULT_KEY to "Logged out of Spotify")
    },
)

/**
 * OAuth 登录流程（PKCE）：校验凭据 → 生成 code_verifier/challenge → 起本地回调服务器 →
 * 开浏览器授权 → 等回调 → 携 verifier 换 token。工具与配置页动作共用。成功返回提示文本。
 */
internal suspend fun performLogin(
    auth: SpotifyAuth,
    clientId: () -> String,
    redirectUri: () -> String,
    appContext: () -> Context?,
): String = withContext(Dispatchers.IO) {
    if (clientId().isBlank()) {
        throw IllegalStateException("Configure Client ID in the plugin settings first")
    }
    val context = appContext() ?: throw IllegalStateException("Plugin has no attached context")
    val redirect = redirectUri()
    val state = SpotifyAuth.newState()
    val codeVerifier = SpotifyAuth.newCodeVerifier()
    val codeChallenge = SpotifyAuth.codeChallengeS256(codeVerifier)
    // 先绑定回调端口，再开浏览器授权；否则回调到达时端口未监听会失败。
    val server = startCallbackServer(redirect, state)
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                auth.authorizeUrl(clientId(), redirect, state, codeChallenge).toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val code = server.await()
        auth.exchangeCode(code, redirect, codeVerifier)
    } finally {
        server.close()
    }
    "Spotify authorization succeeded"
}
