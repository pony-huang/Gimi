package github.ponyhuang.gimi.plugin.spotify.tools

import android.content.Context
import android.content.Intent
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyAuth
import github.ponyhuang.gimi.plugin.spotify.startCallbackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

internal fun authTools(
    auth: SpotifyAuth,
    clientId: () -> String,
    clientSecret: () -> String,
    redirectUri: () -> String,
    appContext: () -> Context?,
): List<BaseTool> = listOf(
    SpotifyLoginTool(auth, clientId, clientSecret, redirectUri, appContext),
    SpotifyAuthStatusTool(auth),
    SpotifyLogoutTool(auth),
)

/**
 * OAuth 登录流程：校验凭据 → 起本地回调服务器 → 开浏览器授权 → 等回调 → 换 token。
 * 工具与配置页动作共用。成功返回提示文本。
 */
internal suspend fun performLogin(
    auth: SpotifyAuth,
    clientId: () -> String,
    clientSecret: () -> String,
    redirectUri: () -> String,
    appContext: () -> Context?,
): String = withContext(Dispatchers.IO) {
    if (clientId().isBlank() || clientSecret().isBlank()) {
        throw IllegalStateException("Configure Client ID and Client Secret in the plugin settings first")
    }
    val context = appContext() ?: throw IllegalStateException("Plugin has no attached context")
    val redirect = redirectUri()
    val state = SpotifyAuth.newState()
    // 先绑定回调端口，再开浏览器授权；否则回调到达时端口未监听会失败。
    val server = startCallbackServer(redirect, state)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, auth.authorizeUrl(clientId(), redirect, state).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val code = server.await()
        auth.exchangeCode(code, redirect)
    } finally {
        server.close()
    }
    "Spotify authorization succeeded"
}

/** spotify_login — 打开浏览器完成 OAuth 授权。 */
private class SpotifyLoginTool(
    private val auth: SpotifyAuth,
    private val clientId: () -> String,
    private val clientSecret: () -> String,
    private val redirectUri: () -> String,
    private val appContext: () -> Context?,
) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> =
        mapOf(SpotifyTool.RESULT_KEY to performLogin(auth, clientId, clientSecret, redirectUri, appContext))

    companion object {
        const val NAME: String = "spotify_login"
        const val DESCRIPTION: String =
            "Open the browser to complete Spotify OAuth authorization (configure Client ID/Secret first). " +
                "Required before first use."
    }
}

/** spotify_auth_status — 查看当前授权状态。 */
private class SpotifyAuthStatusTool(private val auth: SpotifyAuth) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val authorized = auth.hasAccessToken()
        return mapOf(
            SpotifyTool.RESULT_KEY to mapOf(
                "authorized" to authorized,
                "message" to if (authorized) "Authorized" else "Not authorized, run spotify_login first",
            ),
        )
    }

    companion object {
        const val NAME: String = "spotify_auth_status"
        const val DESCRIPTION: String = "Check whether the Spotify plugin is authorized (logged in)."
    }
}

/** spotify_logout — 清除本地 token。 */
private class SpotifyLogoutTool(private val auth: SpotifyAuth) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        auth.clearTokens()
        return mapOf(SpotifyTool.RESULT_KEY to "Logged out of Spotify")
    }

    companion object {
        const val NAME: String = "spotify_logout"
        const val DESCRIPTION: String = "Clear the local Spotify token; run spotify_login again afterwards."
    }
}
