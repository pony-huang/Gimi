package github.ponyhuang.gimi.plugin.spotify

import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthTest {

    @Test
    fun authorizeUrlContainsCoreParamsAndScopes() {
        val url = buildAuthorizeUrl("cid", "http://127.0.0.1:8888/callback", "st-1", SpotifyAuth.SCOPES)

        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("client_id=cid"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=st-1"))
        assertTrue(url.contains("show_dialog=true"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A8888%2Fcallback"))
        // 关键 scope 都在
        assertTrue(url.contains("playlist-modify-public"))
        assertTrue(url.contains("user-modify-playback-state"))
        assertTrue(url.contains("user-top-read"))
    }

    @Test
    fun parseTokenResponseExtractsFields() {
        val bundle = parseTokenResponse(
            JSONObject()
                .put("access_token", "ACC")
                .put("token_type", "Bearer")
                .put("expires_in", 3600)
                .put("refresh_token", "REF"),
        )

        assertEquals("ACC", bundle.accessToken)
        assertEquals("REF", bundle.refreshToken)
        assertEquals(3600L, bundle.expiresIn)
    }

    @Test
    fun parseTokenResponseAllowsMissingRefreshToken() {
        val bundle = parseTokenResponse(JSONObject().put("access_token", "ACC"))

        assertEquals("ACC", bundle.accessToken)
        assertNull(bundle.refreshToken)
    }

    @Test
    fun parseRedirectUriExtractsPortAndPath() {
        assertEquals(8888, parseRedirectUri("http://127.0.0.1:8888/callback").port)
        assertEquals("/callback", parseRedirectUri("http://127.0.0.1:8888/callback").path)
        assertEquals(80, parseRedirectUri("http://localhost/callback").port)
    }

    @Test
    fun parseRedirectUriRejectsNonLoopback() {
        try {
            parseRedirectUri("https://example.com/callback")
            assertFalse("should have thrown", true)
        } catch (e: SpotifyAuthException) {
            assertTrue(e.message.orEmpty().contains("redirect_uri"))
        }
    }

    @Test
    fun parseRedirectRequestSuccess() {
        val result = parseRedirectRequest(
            "GET /callback?code=abc123&state=st-1 HTTP/1.1",
            "/callback",
            "st-1",
        )

        assertTrue(result is RedirectResult.Success)
        assertEquals("abc123", (result as RedirectResult.Success).code)
    }

    @Test
    fun parseRedirectRequestRejectsStateMismatch() {
        val result = parseRedirectRequest(
            "GET /callback?code=abc123&state=wrong HTTP/1.1",
            "/callback",
            "st-1",
        )

        assertTrue(result is RedirectResult.Error)
        assertTrue((result as RedirectResult.Error).message.contains("State"))
    }

    @Test
    fun parseRedirectRequestSurfacesOauthError() {
        val result = parseRedirectRequest(
            "GET /callback?error=access_denied&state=st-1 HTTP/1.1",
            "/callback",
            "st-1",
        )

        assertTrue(result is RedirectResult.Error)
        assertTrue((result as RedirectResult.Error).message.contains("access_denied"))
    }

    @Test
    fun newStateIsFixedLengthAlphanumeric() {
        repeat(20) {
            val state = SpotifyAuth.newState()
            assertEquals(16, state.length)
            assertTrue(state.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' })
        }
    }

    /**
     * 真实 socket 回归测试：模拟浏览器请求回调，验证服务器能返回成功页且不因
     * 提前关闭流而失败（此前 `.use` 关闭 reader 连带关闭 socket，浏览器报「无法访问页面」）。
     */
    @Test
    fun callbackServerServesSuccessPageAndReturnsCode() {
        val port = 43218
        val server = startCallbackServer("http://127.0.0.1:$port/callback", "st-1")
        val codeFuture = CompletableFuture<String>()
        val serverThread = Thread { codeFuture.complete(server.await()) }
        try {
            serverThread.start()
            val socket = Socket("127.0.0.1", port)
            socket.getOutputStream().write(
                "GET /callback?code=abc123&state=st-1 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(),
            )
            socket.getOutputStream().flush()
            val response = socket.getInputStream().bufferedReader().use { it.readText() }
            socket.close()
            serverThread.join(5_000)

            assertEquals("abc123", codeFuture.get(5, TimeUnit.SECONDS))
            assertTrue(response.contains("HTTP/1.1 200"))
            assertTrue(response.contains("Spotify sign-in succeeded"))
        } finally {
            server.close()
        }
    }

    @Test
    fun needsRefreshUsesBuffer() {
        val now = 1_000_000L
        val buffer = 5 * 60 * 1000L

        // 过期时刻在 now+buffer 之后：无需刷新。
        assertFalse(needsRefresh(now + buffer + 1, now, buffer))
        // 恰在缓冲区内或已过期：需刷新。
        assertTrue(needsRefresh(now + buffer, now, buffer))
        assertTrue(needsRefresh(now, now, buffer))
        assertTrue(needsRefresh(now - 1_000, now, buffer))
    }
}
