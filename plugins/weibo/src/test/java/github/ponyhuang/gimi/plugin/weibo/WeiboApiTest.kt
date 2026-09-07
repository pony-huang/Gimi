package github.ponyhuang.gimi.plugin.weibo

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class WeiboApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newApi(
        clock: () -> Long = { System.currentTimeMillis() },
        sleeps: MutableList<Long> = mutableListOf(),
    ): WeiboApi = WeiboApi(
        baseUrl = server.url("/").toString().trimEnd('/'),
        appId = "client-id",
        appSecret = "client-secret",
        clock = clock,
        sleeper = { sleeps += it },
    )

    private fun okEnvelope(code: Int = 0, data: Any? = JSONObject()): String =
        JSONObject().put("code", code).put("message", "ok").put("data", data).toString()

    private fun tokenResponseBody(value: String = "the-token", expireIn: Int = 7200): String =
        JSONObject()
            .put("code", 0)
            .put("message", "ok")
            .put(
                "data",
                JSONObject().put("token", value).put("expire_in", expireIn),
            )
            .toString()

    @Test
    fun firstBusinessCallFetchesTokenThenCallsBusinessEndpoint() {
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))

        val api = newApi()
        val data = api.search("hello")

        assertEquals("A", data.optString("word"))

        val tokenRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/open/auth/ws_token", tokenRequest.path)
        assertEquals("POST", tokenRequest.method)
        val tokenBody = JSONObject(tokenRequest.body.readUtf8())
        assertEquals("client-id", tokenBody.getString("app_id"))
        assertEquals("client-secret", tokenBody.getString("app_secret"))

        val business = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("GET", business.method)
        assertTrue(business.path!!.startsWith("/open/wis/search_query"))
        assertTrue(business.path!!.contains("query=hello"))
        // token 必须作为 query 参数带上（POST 也一样）。
        assertTrue(business.path!!.contains("token=the-token"))
    }

    @Test
    fun secondCallReusesCachedTokenWithoutHittingAuthEndpoint() {
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "B"))).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.search("first")
        api.search("second")

        // 3 个入队 = 1 个 token + 2 个业务，没有再次换 token。
        assertEquals(1, server.requestCount - 2)
    }

    @Test
    fun invalidateTokenForcesNewFetchOnNextCall() {
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "new-token")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "B"))).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.search("first")
        api.invalidateToken()
        api.search("second")

        // 顺序：1st token / 1st biz / 2nd token / 2nd biz；最后一个请求必须带新 token。
        val requests = drainRequests()
        assertEquals(4, requests.size)
        assertTrue(requests.last().path!!.startsWith("/open/wis/search_query"))
        assertTrue(requests.last().path!!.contains("token=new-token"))
    }

    @Test
    fun serverTokenInvalidResponseTriggersRefreshAndRetryOnce() {
        // 第一个 token 拿到了，业务接口先回 40100；触发 invalidate + retry；第二次换 token 后业务接口回成功。
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "old")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(code = CODE_TOKEN_INVALID)).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "new")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))

        val api = newApi()
        val data = api.search("hello")
        assertEquals("A", data.optString("word"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun tokenFetchRetriesTransientErrorsThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))

        val sleeps = mutableListOf<Long>()
        val api = newApi(sleeps = sleeps)
        api.search("hello")

        // 503 -> 1s 退避 -> 成功；不再二次退避。
        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun tokenFetchGivesUpAfterTwoRetries() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        val sleeps = mutableListOf<Long>()
        val api = newApi(sleeps = sleeps)
        val ex = assertThrows(WeiboHttpException::class.java) {
            api.search("hello")
        }
        assertEquals(503, ex.status)
        // 1s -> 2s 退避，两次重试耗尽后抛出。
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun nonRetryableStatusIsSurfacedImmediately() {
        server.enqueue(MockResponse().setResponseCode(403))

        val sleeps = mutableListOf<Long>()
        val api = newApi(sleeps = sleeps)
        val ex = assertThrows(WeiboHttpException::class.java) {
            api.search("hello")
        }
        assertEquals(403, ex.status)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun configureWithNewCredentialsInvalidatesCachedToken() {
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "token-for-A")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))

        val api = WeiboApi(
            baseUrl = server.url("/").toString().trimEnd('/'),
            appId = "client-id-A",
            appSecret = "secret-A",
        )
        api.search("first")

        var requests = drainRequests()
        assertEquals(2, requests.size)
        assertEquals("token-for-A", extractTokenQuery(requests.last().path!!))

        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "token-for-B")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "B"))).setHeader("Content-Type", "application/json"))

        api.configure(server.url("/").toString().trimEnd('/'), "client-id-B", "secret-B")
        api.search("second")
        requests = drainRequests()
        assertEquals(2, requests.size)
        assertEquals("token-for-B", extractTokenQuery(requests.last().path!!))
    }

    @Test
    fun configureWithSameCredentialsKeepsCachedToken() {
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "token-A")).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "A"))).setHeader("Content-Type", "application/json"))

        val api = WeiboApi(
            baseUrl = server.url("/").toString().trimEnd('/'),
            appId = "client-id",
            appSecret = "secret",
        )
        api.search("first")
        api.configure(server.url("/").toString().trimEnd('/'), "client-id", "secret")
        // 不再换 token，第二个业务调用直接复用缓存。
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("word", "B"))).setHeader("Content-Type", "application/json"))
        api.search("second")
        assertEquals(3, server.requestCount)
    }

    @Test
    fun tokenStatusReportsMaskedPreviewAndExpiry() {
        server.enqueue(MockResponse().setBody(tokenResponseBody(value = "abcdef1234567890")).setHeader("Content-Type", "application/json"))
        val api = newApi()
        val status = api.tokenStatus()
        assertEquals(true, status["configured"])
        assertEquals("abcdef***", status["token_preview"])
        assertEquals(7200L, status["expires_in_seconds"])
        assertNotNull(status["acquired_at"])
        assertNotNull(status["expires_at"])
    }

    @Test
    fun tokenStatusFailsWithoutCredentials() {
        val api = WeiboApi()
        assertFalse(api.hasCredentials())
        val ex = assertThrows(IllegalStateException::class.java) { api.tokenStatus() }
        assertEquals(WeiboApi.MISSING_CREDENTIALS_MESSAGE, ex.message)
    }

    @Test
    fun writeOperationAppendsPluginDeclarationConstant() {
        // 写接口固定带 AI_MODEL_NAME_DECLARATION 常量；用户不再需要在插件设置里填。
        // 该字段是服务端审计字段，对取值完全开放（curl 实测任意字符串都接受），没必要
        // 把内部细节漏给用户。
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONObject().put("code", 0).put("msg", "ok"))).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.crowdPost("动漫", "正文")

        val requests = drainRequests()
        assertEquals(2, requests.size)
        val body = JSONObject(requests.last().body.readUtf8())
        assertEquals(WeiboApi.AI_MODEL_NAME_DECLARATION, body.optString("ai_model_name"))
        assertEquals("动漫", body.optString("topic_name"))
        assertEquals("正文", body.optString("status"))
    }

    @Test
    fun crowdTopicsReturnsNamesAsJsonArray() {
        server.enqueue(MockResponse().setBody(tokenResponseBody()).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(okEnvelope(data = JSONArray().put("动漫").put("游戏"))).setHeader("Content-Type", "application/json"))
        val api = newApi()
        val topics = api.crowdTopicNames()
        assertEquals(2, topics.length())
        assertEquals("动漫", topics.optString(0))
    }

    @Test
    fun invalidTokenInRequestBodyIsReportedAsApiError() {
        // token 接口返回了 40005（参数错误），业务接口复用旧 token 失败应该让上层看见。
        server.enqueue(MockResponse().setBody(
            JSONObject().put("code", 40005).put("message", "token invalid").toString()
        ).setHeader("Content-Type", "application/json"))
        val api = newApi()
        val ex = assertThrows(WeiboApiException::class.java) { api.search("hello") }
        assertEquals(40005, ex.code)
    }

    /** 排空已经入队的所有请求，按录制顺序返回。 */
    private fun drainRequests(): List<RecordedRequest> {
        val collected = mutableListOf<RecordedRequest>()
        while (true) {
            val request = server.takeRequest(50, TimeUnit.MILLISECONDS) ?: break
            collected += request
        }
        return collected
    }

    private fun extractTokenQuery(path: String): String? {
        val match = Regex("token=([^&]+)").find(path) ?: return null
        return match.groupValues[1]
    }

    // region ---- 真实服务端响应 fixture（2026-09-07 curl 直打 open-im.api.weibo.com）----
    // 每个 fixture 对应一次真实 curl 调用，响应体直接照搬服务端原文。
    // 目的：把 11 个端点的真实格式锁死，避免任何人（包括我自己）再去抄 openclaw-weibo
    // 参考或凭印象猜端点格式——上次因为抄 `v_openclaw*` 这种不存在的英文 SID 导致 hot_search
    // 全部 40001。

    /** 偷懒 helper：先排好一个 token 响应，调用方只关心业务响应。 */
    private fun enqueueToken(value: String = "the-token") {
        server.enqueue(MockResponse().setBody(tokenResponseBody(value)).setHeader("Content-Type", "application/json"))
    }

    @Test
    fun realFixture_hotSearchMainBoardReturnsRealTrendingData() {
        // 真实响应：主榜 #1 小米澎程（2026-09-07 22:43 抓取）。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """
            {"code":0,"data":{"callTime":"2026-09-07 22:43","data":[
              {"id":1,"word":"小米澎程","num":1897812,"cat":"主榜",
               "app_query_link":"sinaweibo://searchall?q=小米澎程",
               "h5_query_link":"https://m.weibo.cn/search?containerid=100103type=1&q=小米澎程",
               "flag":2,"flag_link":"https://simg.s.weibo.com/20210226_hot_small.png"}
            ],"source":"来自于微博热搜"},"message":"success"}
            """.trimIndent(),
        ).setHeader("Content-Type", "application/json"))

        val data = newApi().hotSearch("主榜", 1)
        val first = data.getJSONArray("data").getJSONObject(0)
        assertEquals("小米澎程", first.getString("word"))
        assertEquals(1, first.getInt("id"))
        assertEquals(1897812, first.getInt("num"))
        assertEquals("主榜", first.getString("cat"))
    }

    @Test
    fun realFixture_hotSearchInvalidCategoryReturns40001WithBoardHint() {
        // 真实响应：旧 SID `v_openclaw` 被服务端原样拒掉，message 里把可选中文榜名列出来。
        // 这是我之前抄 openclaw 参考踩到的坑——服务端对 category 只接受中文本体。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":40001,"message":"无效的榜单类型。可选值：[社会榜, 体育榜, 生活榜, 主榜, 科技榜, acg榜, 文娱榜]"}""",
        ).setHeader("Content-Type", "application/json"))

        val ex = assertThrows(WeiboApiException::class.java) {
            newApi().hotSearch("v_openclaw", 1)
        }
        assertEquals(40001, ex.code)
        assertTrue(ex.message!!.contains("无效的榜单类型"))
        assertTrue(ex.message!!.contains("主榜"))
    }

    @Test
    fun realFixture_searchQueryReturnsAiSummaryNotRawStatuses() {
        // 真实响应：智搜 data 是 AI 摘要（msg / completed / analyzing / refused），不是微博列表。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """
            {"code":0,"data":{
              "analyzing":false,"callTime":"2026-09-07 22:50","completed":true,
              "msg":"小米澎程系列正式上市，四款车型20.99万至29.99万元",
              "msg_format":"markdown","msg_json":"{}","noContent":false,
              "profile_image_url":"","query":"小米车","reference_num":14,
              "refused":false,"scheme":"sinaweibo://cardlist?...","source":"来自于微博智搜",
              "status":2,"status_stage":4,"version":"2026-09-07 04:10:48"
            },"message":"success"}
            """.trimIndent(),
        ).setHeader("Content-Type", "application/json"))

        val data = newApi().search("小米车")
        assertEquals(true, data.getBoolean("completed"))
        assertEquals(false, data.getBoolean("analyzing"))
        assertTrue(data.getString("msg").contains("小米澎程"))
        assertEquals("markdown", data.getString("msg_format"))
    }

    @Test
    fun realFixture_userStatusForEmptyAccountReturnsEmptyStatuses() {
        // 真实响应：测试号没发过微博 → data.statuses 是空数组（不是 null、不是报错）。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":0,"data":{"statuses":[]},"message":"success"}""",
        ).setHeader("Content-Type", "application/json"))

        val data = newApi().userStatus(3, null)
        assertEquals(0, data.getJSONArray("statuses").length())
    }

    @Test
    fun realFixture_topicNamesReturnsJsonArrayOfStrings() {
        // 真实响应：data 顶层就是超话名字符串数组（不是包在 statuses/comments 这种结构里）。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":0,"data":["赛博茶馆"],"message":"success"}""",
        ).setHeader("Content-Type", "application/json"))

        val topics = newApi().crowdTopicNames()
        assertEquals(1, topics.length())
        assertEquals("赛博茶馆", topics.getString(0))
    }

    @Test
    fun realFixture_crowdPostServerReturnsNegativeOneWithNullMessage() {
        // 真实响应：服务端对写接口失败的错误模式是外层 `{code:-1, message:"...: null"}`，
        // 与 projectWriteResult 期望的"外层 code=0、内层 code 决定 success"模式不同——
        // 服务端直接走外层。ai_model_name 现在由插件常量恒发，不再可能因配置缺失触发。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":-1,"message":"发帖失败: null"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdPost("赛博茶馆", "[dryrun]")
        }
        assertEquals(-1, ex.code)
        assertTrue(ex.message!!.contains("发帖失败"))
    }

    @Test
    fun realFixture_crowdPostServerReturnsEnglish40003_isForwardedVerbatim() {
        // 服务端 40003 文案 "ai_model_name must not exceed 64 characters" 是英文，
        // 本地 putAiModelName() 已经先一步把长度 >64 的情况拦在请求前（见
        // writeOperationWithTooLongAiModelNameThrowsBeforeRequest），所以走真实路径
        // 几乎触发不到；但服务端真要回 40003 时，parseEnvelope 必须原样透传而不是退到
        // 本地静态码表的"超过 64 字符"。这里用 mock 强制构造一次服务端 40003 响应来锁住
        // 透传行为。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":40003,"message":"ai_model_name must not exceed 64 characters"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdPost("赛博茶馆", "[dryrun]")
        }
        assertEquals(40003, ex.code)
        // 服务端英文原文优先，不被本地静态码表覆盖。
        assertTrue(ex.message!!.contains("must not exceed 64 characters"))
        // 旧的误导文案绝不出现。
        assertFalse(ex.message!!.contains("sort_type"))
    }

    @Test
    fun realFixture_crowdPostRepeatedReturns20019() {
        // 真实响应：服务端对重复发帖的限速文案"不要太贪心哦，发一次就够啦。"。
        // 不是 42900（那是按天的配额），而是 20019 这种业务级限速——属于按调用次数计数。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":20019,"message":"不要太贪心哦，发一次就够啦。"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdPost("赛博茶馆", "[dryrun]")
        }
        assertEquals(20019, ex.code)
        assertTrue(ex.message!!.contains("不要太贪心哦"))
    }

    @Test
    fun realFixture_crowdPostMissingTopicNameReturns40001() {
        // 真实响应：40001 服务端原文是英文 "topic_name is required"——证明 40001 是
        // 「参数缺失」通用码而不是具体某个字段，文案保持服务端原样。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":40001,"message":"topic_name is required"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdPost("", "[dryrun]")
        }
        assertEquals(40001, ex.code)
        assertTrue(ex.message!!.contains("topic_name is required"))
    }

    @Test
    fun realFixture_crowdCommentTargetNotExistReturnsNegativeOneWithWrappedHttpError() {
        // 真实响应：评论目标微博不存在时，外层 code=-1，message 把内部 HTTP 400
        // + error_code 20101 完整透传——这不是普通 4xx，需要原样抛给模型。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":-1,"message":"发评论失败: HTTP request failed with code: 400, response: {\"error\":\"target weibo does not exist!\",\"error_code\":20101,\"request\":\"/2/comments/create.json\"}"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdComment("9999999999999999", "[dryrun]", null, null)
        }
        assertEquals(-1, ex.code)
        assertTrue(ex.message!!.contains("发评论失败"))
        assertTrue(ex.message!!.contains("target weibo does not exist"))
    }

    @Test
    fun realFixture_crowdReplyTargetNotExistReturnsNegativeOneWithWrappedHttpError() {
        // 与上一条对应的回复路径，message 前缀变成 "回复评论失败: "。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """{"code":-1,"message":"回复评论失败: HTTP request failed with code: 400, response: {\"error\":\"target weibo does not exist!\",\"error_code\":20101,\"request\":\"/2/comments/reply.json\"}"}""",
        ).setHeader("Content-Type", "application/json"))

        val api = newApi()
        api.configure(server.url("/").toString().trimEnd('/'), "id", "secret")
        val ex = assertThrows(WeiboApiException::class.java) {
            api.crowdReply("9999999999999999", "9999999999999999", "[dryrun]", null, null, null)
        }
        assertEquals(-1, ex.code)
        assertTrue(ex.message!!.contains("回复评论失败"))
    }

    @Test
    fun realFixture_searchQueryTolerantToUnknownExtraFields() {
        // 真实响应里还有 status / status_stage / msg_json / profile_image_url 这种
        // mappers 没声明的字段——确认 projectSearch 不会因为 schema 多两个键就崩。
        enqueueToken()
        server.enqueue(MockResponse().setBody(
            """
            {"code":0,"data":{
              "analyzing":false,"completed":true,"msg":"hi","msg_format":"markdown",
              "noContent":false,"refused":false,"reference_num":0,
              "scheme":"sinaweibo://x","source":"来自于微博智搜","status":2,"status_stage":4,
              "version":"v1","callTime":"2026-09-07","_future_field":"unexpected"
            },"message":"success"}
            """.trimIndent(),
        ).setHeader("Content-Type", "application/json"))

        val data = newApi().search("hi")
        // 不抛异常就能拿到核心字段
        assertEquals("hi", data.getString("msg"))
        assertEquals(true, data.getBoolean("completed"))
    }
    // endregion
}