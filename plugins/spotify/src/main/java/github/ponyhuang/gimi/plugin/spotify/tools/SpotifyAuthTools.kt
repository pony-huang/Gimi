package github.ponyhuang.gimi.plugin.spotify.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        throw IllegalStateException("请先在插件配置页填写 Client ID 和 Client Secret")
    }
    val context = appContext() ?: throw IllegalStateException("插件未附加上下文")
    val redirect = redirectUri()
    val state = SpotifyAuth.newState()
    // 先绑定回调端口，再开浏览器授权；否则回调到达时端口未监听会失败。
    val server = auth.startCallbackServer(redirect, state)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(auth.authorizeUrl(clientId(), redirect, state)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val code = server.await()
        auth.exchangeCode(code, redirect)
    } finally {
        server.close()
    }
    "Spotify 授权成功，可以开始使用了"
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
            "打开浏览器完成 Spotify OAuth 授权（需先在插件配置页填写 Client ID/Secret）。首次使用必须调用。"
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
                "message" to if (authorized) "已授权" else "未授权，请先调用 spotify_login",
            ),
        )
    }

    companion object {
        const val NAME: String = "spotify_auth_status"
        const val DESCRIPTION: String = "查看 Spotify 插件的授权状态（是否已登录）。"
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
        return mapOf(SpotifyTool.RESULT_KEY to "已退出 Spotify 登录")
    }

    companion object {
        const val NAME: String = "spotify_logout"
        const val DESCRIPTION: String = "清除本地 Spotify token，之后需重新 spotify_login。"
    }
}
