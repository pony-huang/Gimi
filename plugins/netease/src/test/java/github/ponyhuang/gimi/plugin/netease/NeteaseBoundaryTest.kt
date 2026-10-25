package github.ponyhuang.gimi.plugin.netease

import com.google.adk.kt.tools.ToolContext
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** 请求签名、凭证隔离、写请求和取消属于独立 HTTP 集成的必要风险边界，保留回归覆盖。 */
class NeteaseBoundaryTest {
    private val songId = "E6767DDA85289AA4118775DE0D6BCC32"
    private val secondSongId = "CB328A27F2D13A4ED57B41534F70330E"
    private val playlistId = "1A9910C070E44B82B24CE147AED8DFB8"
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(keys.private.encoded) + "\n-----END PRIVATE KEY-----"
    private lateinit var server: MockWebServer
    private lateinit var api: NeteaseApi
    private val device = JSONObject().put("deviceType", "openapi").put("deviceId", "ncmcli_test").put("clientIp", "203.0.113.7")
    private var credentials: NeteaseCredentials = NeteaseCredentials("", "")
    private val store = MemoryStore()
    private lateinit var account: NeteaseAccount
    private val context = mockk<ToolContext>(relaxed = true)
    private var now = 1_000_000L

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        credentials = NeteaseCredentials("test-app", pem, "test-secret")
        api = NeteaseApi(server.url("/").toString(), clock = { now })
        account = NeteaseAccount(api, store, { credentials }, { now })
    }

    @After fun tearDown() { server.shutdown() }

    private fun enqueue(data: Any = JSONObject(), code: Int = 200) {
        server.enqueue(MockResponse().setBody(JSONObject().put("code", code).put("data", data).toString()))
    }

    private fun tokens(access: String = "secret-access", refresh: String = "secret-refresh", expires: Long = 3600): JSONObject =
        JSONObject().put("accessToken", access).put("refreshToken", refresh).put("expireTime", expires)

    private suspend fun login() {
        enqueue(tokens())
        account.anonymousLogin()
        server.takeRequest()
    }

    private suspend fun memberLogin() {
        login()
        enqueue(JSONObject().put("uniKey", "qr-test").put("qrCodeUrl", "https://163cn.tv/test"))
        account.createQr()
        server.takeRequest()
        enqueue(JSONObject().put("status", 803).put("accessToken", tokens()))
        enqueue(JSONObject().put("nickname", "test-user"))
        account.pollQr()
        server.takeRequest()
        server.takeRequest()
    }

    private suspend fun tool(name: String, args: Map<String, Any?> = emptyMap()): String =
        (neteaseTools(account).single { it.name == name } as NeteaseTool).execute(context, args).toString()

    @Test fun publicIpIsResolvedBeforeSigningWithoutSendingCredentialsToLookup() = runBlocking {
        server.enqueue(MockResponse().setBody("203.0.113.9"))
        enqueue()
        val client = NeteaseApi(server.url("/").toString(), clock = { now }, ipLookupUrls = listOf(server.url("/ip").toString()))
        client.request(credentials, JSONObject().put("deviceId", "test"), "/business")
        val lookup = server.takeRequest()
        assertEquals("/ip", lookup.path)
        assertEquals("GET", lookup.method)
        assertFalse(lookup.path!!.contains("appId"))
        val business = server.takeRequest()
        val actualDevice = JSONObject(business.requestUrl!!.queryParameter("device")!!)
        assertEquals("203.0.113.9", actualDevice.getString("clientIp"))
    }

    @Test fun manifestDiscoveryUsesSignedPostAndReturnsAuthorizedCommands() = runBlocking {
        login()
        enqueue(JSONObject().put("commands", org.json.JSONArray()))
        assertFalse(tool("netease_commands").contains("error"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/openapi/v1/ncm/cli/manifest", request.path)
        val params = JSONObject(request.body.readUtf8())
        assertEquals("secret-access", params.getString("accessToken"))
        val biz = JSONObject(params.getString("bizContent"))
        assertEquals("0.1.7", biz.getString("cli_version"))
        assertEquals(0, biz.getJSONObject("cached_versions").length())
        enqueue(code = 301)
        val error = tool("netease_commands")
        assertTrue(error.contains("未获此接口授权"))
        assertFalse(error.contains("secret-access"))
        server.takeRequest()
        Unit
    }

    @Test fun songOptionsMapToPlatformFieldsWithoutChangingTrialPermissions() = runBlocking {
        login()
        enqueue(JSONObject().put("isAudition", true))
        val result = tool("netease_song_detail", mapOf("song_id" to songId,
            "effects" to "test-effect", "level" to "test-level", "immerse_type" to "test-immerse", "trial_scene" to "cli"))
        assertTrue(result.contains("isAudition=true"))
        val biz = JSONObject(server.takeRequest().requestUrl!!.queryParameter("bizContent")!!)
        assertEquals("test-effect", biz.getString("effects"))
        assertEquals("test-level", biz.getString("level"))
        assertEquals("test-immerse", biz.getString("immerseType"))
        assertEquals("cli", biz.getString("trialScene"))
        assertEquals(songId, biz.getString("songId"))
        assertEquals(320, biz.getInt("bitrate"))
        assertTrue(tool("netease_song_detail", mapOf("song_id" to songId, "level" to " ")).contains("error"))
    }

    @Test fun canonicalOmitsEmptyAndSignWithoutEncoding() {
        assertEquals("accessToken=a+b&appId=测试&bizContent={x=y}", NeteaseApi.canonical(mapOf(
            "bizContent" to "{x=y}", "sign" to "ignored", "empty" to "", "null" to null, "appId" to "测试", "accessToken" to "a+b",
        )))
    }

    @Test fun getUsesSignedRawValuesAndRequiredHeaders() = runBlocking {
        enqueue(JSONObject().put("title", "歌曲"))
        val result = api.request(credentials, device, "/test", JSONObject().put("songId", 186016), "a+b") as JSONObject
        assertEquals("歌曲", result.getString("title"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("ncm-0.1.7", request.getHeader("User-Agent"))
        assertEquals("https://music.163.com/", request.getHeader("Referer"))
        val url = request.requestUrl!!
        val params = url.queryParameterNames.associateWith { url.queryParameter(it) }
        assertEquals("1000000", params["timestamp"])
        assertEquals("a+b", params["accessToken"])
        assertEquals(device.toString(), params["device"])
        assertTrue(Signature.getInstance("SHA256withRSA").run {
            initVerify(keys.public)
            update(NeteaseApi.canonical(params).toByteArray())
            verify(Base64.getDecoder().decode(params["sign"]))
        })
    }

    @Test fun postCarriesJsonAndAcceptsLiteralNewlines() = runBlocking {
        enqueue()
        api.request(NeteaseCredentials("app", pem.replace("\n", "\\n")), device, "/test", JSONObject().put("songIdList", org.json.JSONArray(listOf(1, 2))), post = true)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(0, request.requestUrl!!.querySize)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = JSONObject(request.body.readUtf8())
        assertFalse(body.has("accessToken"))
        assertEquals(2, JSONObject(body.getString("bizContent")).getJSONArray("songIdList").length())
    }

    @Test fun rawBase64Pkcs8KeySignsLikePem() {
        val params = mapOf("appId" to "test-app", "timestamp" to "1000000")
        val raw = Base64.getEncoder().encodeToString(keys.private.encoded)
        assertEquals(NeteaseApi.sign(params, pem), NeteaseApi.sign(params, raw))
        assertEquals(NeteaseApi.sign(params, pem), NeteaseApi.sign(params, raw.chunked(64).joinToString("\n")))
    }

    @Test fun ambiguousPostFailureDoesNotReplayRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { api.request(credentials, device, "/write", post = true); fail() } catch (_: java.io.IOException) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun missingCredentialsAndInvalidPrivateKeyNeverHitNetwork() = runBlocking {
        for (c in listOf(NeteaseCredentials("", pem), NeteaseCredentials("id", "private-key-file"))) {
            try { api.request(c, device, "/test"); fail() } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun httpAndBusinessErrorsNeverEchoServerSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret-access private-key"))
        try { api.request(credentials, device, "/test"); fail() } catch (e: NeteaseApiException) {
            assertEquals(401, e.code)
            assertFalse(e.message!!.contains("secret"))
        }
        enqueue(JSONObject().put("accessToken", "secret-access"), 1406)
        try { api.request(credentials, device, "/test"); fail() } catch (e: NeteaseApiException) {
            assertEquals(1406, e.code)
            assertTrue(e.message!!.contains("版权"))
        }
    }

    @Test fun malformedJsonIsControlledFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>private-key</html>"))
        try { api.request(credentials, device, "/test"); fail() } catch (e: NeteaseApiException) {
            assertEquals(0, e.code)
            assertFalse(e.message!!.contains("private-key"))
        }
    }

    @Test fun redirectsAndWriteFailuresAreNotRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/leak")))
        try { api.request(credentials, device, "/write", post = true); fail() } catch (_: NeteaseApiException) { }
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("unavailable"))
        try { api.request(credentials, device, "/write", post = true); fail() } catch (_: NeteaseApiException) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun cancellationPropagatesThroughToolAndCancelsCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async { tool("netease_login_anonymous") }
        while (server.requestCount == 0) delay(10)
        withTimeout(2_000) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
        assertEquals(1, server.requestCount)
        assertFalse(account.status()["authorized"] as Boolean)
    }

    @Test fun qrSuccessReadsNestedTokensWithoutExposingThem() = runBlocking {
        memberLogin()
        assertEquals(true, account.status()["authorized"])
        assertEquals(false, account.status()["anonymous"])
        assertEquals("secret-access", store.session.accessToken)
        assertFalse(tool("netease_auth_status").contains("secret-access"))
        enqueue(JSONObject().put("nickname", "测试"))
        assertTrue(tool("netease_profile").contains("测试"))
        assertEquals("secret-access", server.takeRequest().requestUrl!!.queryParameter("accessToken"))
    }

    @Test fun rejectedAccountIsNotSavedAsAuthorizedAfterQrConfirmation() = runBlocking {
        login()
        enqueue(JSONObject().put("uniKey", "qr-test").put("qrCodeUrl", "https://163cn.tv/test"))
        account.createQr()
        server.takeRequest()
        enqueue(JSONObject().put("status", 803).put("accessToken", tokens()))
        enqueue(code = 301)
        try { account.pollQr(); fail() } catch (e: NeteaseApiException) { assertEquals(301, e.code) }
        assertEquals(true, account.status()["anonymous"])
    }

    @Test fun anonymousCannotReadAccountOrWritePlaylist() = runBlocking {
        login()
        assertTrue(tool("netease_profile").contains("扫码登录"))
        assertTrue(tool("netease_playlist_add_songs", mapOf("playlist_id" to playlistId, "song_ids" to listOf(songId))).contains("扫码登录"))
        assertEquals(1, server.requestCount)
    }

    @Test fun changedCredentialsInvalidateSessionAndPendingQr() = runBlocking {
        login()
        credentials = NeteaseCredentials("different-app", pem, "test-secret")
        assertEquals(false, account.status()["authorized"])
        assertTrue(tool("netease_song_detail", mapOf("song_id" to songId)).contains("先匿名登录"))
        assertEquals(1, server.requestCount)
    }

    @Test fun firstQrAutomaticallyBootstrapsAnonymousToken() = runBlocking {
        enqueue(tokens())
        enqueue(JSONObject().put("uniKey", "qr-test").put("qrCodeUrl", "https://163cn.tv/test"))
        val result = account.createQr()
        assertEquals("https://163cn.tv/test", result["qr_code_url"])
        assertEquals("/openapi/music/basic/oauth2/login/anonymous", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("secret-access", server.takeRequest().requestUrl!!.queryParameter("accessToken"))
        assertEquals(true, account.status()["anonymous"])
    }

    @Test fun expiredSessionCanStartNewQrWithoutRefreshingOldCredentials() = runBlocking {
        login()
        now += 3_600_000
        enqueue(tokens("fresh-anonymous"))
        enqueue(JSONObject().put("uniKey", "new-qr").put("qrCodeUrl", "https://163cn.tv/test"))
        account.createQr()
        assertEquals("/openapi/music/basic/oauth2/login/anonymous", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("fresh-anonymous", server.takeRequest().requestUrl!!.queryParameter("accessToken"))
    }

    @Test fun expiredQrDoesNotPollAndLogoutClearsSession() = runBlocking {
        login()
        enqueue(JSONObject().put("uniKey", "qr-test").put("qrCodeUrl", "https://163cn.tv/test"))
        account.createQr()
        now += 300_001
        assertTrue(tool("netease_login_poll").contains("新的登录二维码"))
        account.logout()
        assertEquals(false, account.status()["authorized"])
        assertEquals(2, server.requestCount)
    }

    @Test fun qrWaitingAndMalformedSuccessDoNotAuthorize() = runBlocking {
        login()
        enqueue(JSONObject().put("uniKey", "qr-test").put("qrCodeUrl", "https://163cn.tv/test"))
        account.createQr()
        enqueue(JSONObject().put("status", 802))
        assertEquals(802, account.pollQr()["status"])
        enqueue(JSONObject().put("status", 803).put("accessToken", "not-a-token-object"))
        assertTrue(tool("netease_login_poll").contains("有效登录态"))
        assertEquals(true, account.status()["anonymous"])
    }

    @Test fun expiredSessionRefreshesOnceBeforeBusinessCall() = runBlocking {
        login()
        now += 3_600_000
        enqueue(tokens("new-access", "new-refresh"))
        enqueue(JSONObject().put("id", 186016))
        val result = tool("netease_song_detail", mapOf("song_id" to songId))
        assertTrue(result.contains("186016"))
        val refresh = server.takeRequest()
        assertEquals("/openapi/music/basic/user/oauth2/token/refresh/v2", refresh.requestUrl!!.encodedPath)
        val biz = JSONObject(refresh.requestUrl!!.queryParameter("bizContent")!!)
        assertEquals("test-secret", biz.getString("clientSecret"))
        assertEquals("secret-refresh", biz.getString("refreshToken"))
        assertEquals("new-access", server.takeRequest().requestUrl!!.queryParameter("accessToken"))
    }

    @Test fun refreshFailureDoesNotSubmitBusinessRequest() = runBlocking {
        login()
        now += 3_600_000
        enqueue(code = 401)
        assertTrue(tool("netease_song_detail", mapOf("song_id" to songId)).contains("401"))
        assertEquals(2, server.requestCount)
    }

    @Test fun invalidArgumentsNeverPerformHttp() = runBlocking {
        for (args in listOf(mapOf("song_id" to 0), mapOf("song_id" to 1.2), mapOf("song_id" to songId, "bitrate" to 0))) {
            assertTrue(tool("netease_song_detail", args).contains("error"))
        }
        assertTrue(tool("netease_playlist_add_songs", mapOf("playlist_id" to 1, "song_ids" to listOf(0))).contains("error"))
        assertTrue(tool("netease_playlists", mapOf("kind" to "unknown")).contains("error"))
        assertEquals(0, server.requestCount)
    }

    @Test fun playlistWriteMapsExactFieldsAndDoesNotLeakCredentials() = runBlocking {
        memberLogin()
        enqueue(JSONObject().put("ok", true).put("nested", JSONObject().put("accessToken", "unexpected-secret")))
        val result = tool("netease_playlist_add_songs", mapOf("playlist_id" to playlistId, "song_ids" to listOf(songId, secondSongId, songId)))
        assertFalse(result.contains("unexpected-secret"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        val biz = JSONObject(body.getString("bizContent"))
        assertEquals(playlistId, biz.getString("playlistId"))
        assertEquals(listOf(songId, secondSongId), (0 until biz.getJSONArray("songIdList").length()).map { biz.getJSONArray("songIdList").getString(it) })
    }

    @Test fun searchMapsEachDocumentedEndpointAndPreservesEncryptedIds() = runBlocking {
        login()
        for ((kind, endpoint) in mapOf("song" to "song/get/v3", "album" to "album/get/v2",
            "artist" to "artists/get/v2", "playlist" to "playlist/get/v2")) {
            enqueue(JSONObject().put("records", org.json.JSONArray().put(JSONObject().put("id", songId))))
            assertTrue(tool("netease_search", mapOf("keyword" to "测试 歌曲", "kind" to kind, "limit" to 5, "offset" to 10)).contains(songId))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/openapi/music/basic/search/$endpoint", request.requestUrl!!.encodedPath)
            val biz = JSONObject(request.requestUrl!!.queryParameter("bizContent")!!)
            assertEquals("测试 歌曲", biz.getString("keyword"))
            assertEquals(5, biz.getInt("limit"))
            assertEquals(10, biz.getInt("offset"))
        }
        enqueue()
        tool("netease_search", mapOf("keyword" to "测试"))
        val request = server.takeRequest()
        assertEquals("/openapi/music/basic/search/song/get/v3", request.requestUrl!!.encodedPath)
        val biz = JSONObject(request.requestUrl!!.queryParameter("bizContent")!!)
        assertEquals(30, biz.getInt("limit"))
        assertEquals(0, biz.getInt("offset"))
    }

    @Test fun searchAuxiliaryToolsUseDocumentedParameters() = runBlocking {
        login()
        for ((name, endpoint) in mapOf("netease_search_combined" to "complex/search",
            "netease_search_suggest" to "search/suggest/keyword/get/v2",
            "netease_search_hot" to "search/hot/keyword/get/v2")) {
            enqueue()
            assertFalse(tool(name, if (name == "netease_search_hot") emptyMap() else mapOf("keyword" to "音乐")).contains("error"))
            val request = server.takeRequest()
            assertEquals("/openapi/music/basic/$endpoint", request.requestUrl!!.encodedPath)
            val biz = JSONObject(request.requestUrl!!.queryParameter("bizContent")!!)
            if (name == "netease_search_hot") assertEquals(10, biz.getInt("limit"))
            else assertEquals("音乐", biz.getString("keyword"))
        }
    }

    @Test fun lyricsPreserveNoLyricAndSelectLineOrWordEndpoint() = runBlocking {
        login()
        for (wordByWord in listOf(false, true)) {
            enqueue(JSONObject().put("noLyric", true))
            assertTrue(tool("netease_lyrics", mapOf("song_id" to songId, "word_by_word" to wordByWord)).contains("noLyric=true"))
            val request = server.takeRequest()
            val suffix = if (wordByWord) "word/by/word/get" else "get/v2"
            assertEquals("/openapi/music/basic/song/lyric/$suffix", request.requestUrl!!.encodedPath)
            assertEquals(songId, JSONObject(request.requestUrl!!.queryParameter("bizContent")!!).getString("songId"))
        }
    }

    @Test fun newWritesRequireMemberAndMapExactPostFields() = runBlocking {
        login()
        val calls = mapOf(
            "netease_playlist_create" to mapOf("playlist_name" to "我的歌单"),
            "netease_song_like" to mapOf("song_id" to songId, "like" to false),
            "netease_playlist_remove_songs" to mapOf("playlist_id" to playlistId, "song_ids" to listOf(songId, songId)),
        )
        for ((name, args) in calls) assertTrue(tool(name, args).contains("扫码登录"))
        assertEquals(1, server.requestCount)
        memberLogin()
        for ((name, args) in calls) {
            enqueue(JSONObject().put("success", true))
            assertTrue(tool(name, args).contains("success=true"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val biz = JSONObject(JSONObject(request.body.readUtf8()).getString("bizContent"))
            when (name) {
                "netease_playlist_create" -> {
                    assertEquals("/openapi/music/basic/playlist/create", request.path)
                    assertEquals("我的歌单", biz.getString("playlistName"))
                }
                "netease_song_like" -> {
                    assertEquals("/openapi/music/basic/playlist/song/like/v2", request.path)
                    assertEquals(songId, biz.getString("songId"))
                    assertFalse(biz.getBoolean("isLike"))
                    assertFalse(biz.has("like"))
                }
                else -> {
                    assertEquals("/openapi/music/basic/playlist/song/batch/delete", request.path)
                    assertEquals(playlistId, biz.getString("playlistId"))
                    assertEquals(1, biz.getJSONArray("songIdList").length())
                    assertEquals(songId, biz.getJSONArray("songIdList").getString(0))
                }
            }
        }
    }

    @Test fun newToolsRejectInvalidArgumentsBeforeNetwork() = runBlocking {
        val invalid = listOf(
            "netease_search" to mapOf("keyword" to " "),
            "netease_search" to mapOf("keyword" to "x", "kind" to "unknown"),
            "netease_search" to mapOf("keyword" to "x", "limit" to 0),
            "netease_search" to mapOf("keyword" to "x", "offset" to -1),
            "netease_lyrics" to mapOf("song_id" to 186016),
            "netease_lyrics" to mapOf("song_id" to "g".repeat(32)),
            "netease_lyrics" to mapOf("song_id" to songId, "word_by_word" to "true"),
            "netease_song_like" to mapOf("song_id" to songId, "like" to "false"),
            "netease_playlist_create" to mapOf("playlist_name" to " "),
            "netease_playlist_remove_songs" to mapOf("playlist_id" to playlistId, "song_ids" to emptyList<String>()),
            "netease_song_detail" to mapOf("song_id" to songId, "bitrate" to 320000),
        )
        for ((name, args) in invalid) assertTrue("$name $args", tool(name, args).contains("error"))
        assertEquals(0, server.requestCount)
    }

    @Test fun toolsDeclareUniqueNamesAndRequiredArguments() {
        val tools = neteaseTools(account)
        assertEquals(24, tools.size)
        assertEquals(24, tools.map { it.name }.toSet().size)
        tools.forEach { tool -> assertEquals(tool.name, tool.declaration()!!.name) }
        assertEquals(listOf("song_id"), tools.single { it.name == "netease_song_detail" }.declaration()!!.parameters!!.required)
    }

    private class MemoryStore : NeteaseSessionStore {
        override val device: JSONObject get() = JSONObject().put("deviceId", "ncmcli_test").put("clientIp", "203.0.113.7")
        var identity = ""
        var session = NeteaseSession()
        override fun read(identity: String): NeteaseSession = if (this.identity == identity) session else NeteaseSession()
        override fun write(identity: String, session: NeteaseSession) { this.identity = identity; this.session = session }
        override fun clear() { identity = ""; session = NeteaseSession() }
    }
}
