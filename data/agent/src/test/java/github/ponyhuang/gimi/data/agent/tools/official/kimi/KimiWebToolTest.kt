package github.ponyhuang.gimi.data.agent.tools.official.kimi

import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.tools.official.cannedClient
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 付费接口边界：参数、凭据、结果映射、失败与取消；不调用真实服务。 */
class KimiWebToolTest {
    private val context = mockk<ToolContext>(relaxed = true)

    @Test
    fun postsEachOperationWithKimiCredentialsAndPreservesResultFields() = runTest {
        for ((id, operation) in operations) {
            var captured: Request? = null
            val body = if (operation == "fetch") FETCH_BODY else SEARCH_BODY
            val tool = KimiWebTool(id, KimiWebToolApi("kimi-key", cannedClient(200, body) { captured = it }))
            val args = when (operation) {
                "fetch" -> mapOf("url" to "https://example.com/page")
                "search" -> mapOf("text_query" to "Kimi API", "include_content" to true)
                else -> mapOf("text_query" to "Kimi API", "sites" to listOf("kimi.com"),
                    "time_window" to mapOf("start" to "2026", "end" to "2026-10"), "limit" to 20, "timeout_seconds" to 60)
            }
            val result = tool.execute(context, args)
            val request = requireNotNull(captured)
            assertEquals("https://api.moonshot.cn/v1/tools/$operation", request.url.toString())
            assertEquals("Bearer kimi-key", request.header("Authorization"))
            assertEquals("POST", request.method)
            val sent = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            val expected = when (operation) {
                "fetch" -> """{"url":"https://example.com/page"}"""
                "search" -> """{"text_query":"Kimi API","include_content":true,"limit":5,"timeout_seconds":30}"""
                else -> """{"text_query":"Kimi API","sites":["kimi.com"],"time_window":{"start":"2026","end":"2026-10"},"limit":20,"timeout_seconds":60}"""
            }
            assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(sent))
            if (operation == "fetch") {
                assertEquals(mapOf("url" to "https://example.com/page", "title" to "Page", "markdown" to "# Content"), result)
            } else {
                val item = ((result as Map<*, *>)["search_results"] as List<*>).single() as Map<*, *>
                assertEquals("S", item["authority"])
                assertEquals("正文", item["text"])
                assertEquals(listOf(mapOf("text" to "片段", "score" to 1.5)), item["chunks"])
            }
        }
    }

    @Test
    fun rejectsInvalidArgumentsBeforeMakingPaidRequests() = runTest {
        val calls = AtomicInteger()
        val api = KimiWebToolApi("key", cannedClient(200, SEARCH_BODY) { calls.incrementAndGet() })
        val invalid = listOf(
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to " "),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "limit" to 0),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "limit" to 21),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "limit" to 1.5),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "timeout_seconds" to 61),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "include_content" to "true"),
            OfficialToolIds.KIMI_SEARCH to mapOf("text_query" to "q", "sites" to listOf("kimi.com")),
            OfficialToolIds.KIMI_SEARCH_PRO to mapOf("text_query" to "q", "sites" to List(6) { "kimi.com" }),
            OfficialToolIds.KIMI_SEARCH_PRO to mapOf("text_query" to "q", "sites" to listOf("bad site")),
            OfficialToolIds.KIMI_SEARCH_PRO to mapOf("text_query" to "q", "sites" to listOf("(site)")),
            OfficialToolIds.KIMI_SEARCH_PRO to mapOf("text_query" to "q", "time_window" to mapOf("start" to "2026-02-30")),
            OfficialToolIds.KIMI_SEARCH_PRO to mapOf("text_query" to "q", "time_window" to mapOf("start" to "2026-10", "end" to "2026")),
            OfficialToolIds.KIMI_FETCH to mapOf("url" to "file:///private"),
            OfficialToolIds.KIMI_FETCH to mapOf("url" to "https://user:password@example.com"),
        )
        for ((id, args) in invalid) {
            assertTrue("$id $args", (KimiWebTool(id, api).execute(context, args) as Map<*, *>).containsKey("error"))
        }
        assertEquals(0, calls.get())
    }

    @Test
    fun acceptsEmptyResultsAndReportsMalformedOrFailedResponsesWithoutRetry() = runTest {
        for ((code, body) in listOf(200 to """{"search_results":[]}""", 200 to "{}", 200 to "invalid", 408 to "{}", 429 to "{}", 500 to "{}")) {
            val calls = AtomicInteger()
            val tool = KimiWebTool(OfficialToolIds.KIMI_SEARCH, KimiWebToolApi("key", cannedClient(code, body) { calls.incrementAndGet() }))
            val result = tool.execute(context, mapOf("text_query" to "q")) as Map<*, *>
            assertEquals(code != 200 || body != """{"search_results":[]}""", "error" in result)
            assertEquals(1, calls.get())
        }
        val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
        val tool = KimiWebTool(OfficialToolIds.KIMI_FETCH, KimiWebToolApi("key", client))
        assertTrue((tool.execute(context, mapOf("url" to "https://example.com")) as Map<*, *>).containsKey("error"))
        val malformedFetch = KimiWebTool(OfficialToolIds.KIMI_FETCH, KimiWebToolApi("key", cannedClient(200, """{"title":"Page","content":"wrong field"}""")))
        assertTrue((malformedFetch.execute(context, mapOf("url" to "https://example.com")) as Map<*, *>).containsKey("error"))
    }

    @Test
    fun http408DoesNotTriggerOkHttpAutomaticRetry() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(408))
            server.enqueue(MockResponse().setBody("""{"search_results":[]}"""))
            // 仅测试客户端将固定官方地址路由到本地服务器，经过完整 OkHttp 重试链。
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/v1/tools/search")).build())
            }.build()
            val tool = KimiWebTool(OfficialToolIds.KIMI_SEARCH, KimiWebToolApi("key", client))
            assertTrue((tool.execute(context, mapOf("text_query" to "q")) as Map<*, *>).containsKey("error"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun cancellationCancelsTheHttpCallAndPropagates() = runTest {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        var call: okhttp3.Call? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call = chain.call()
            started.countDown()
            released.await(5, TimeUnit.SECONDS)
            throw IOException("canceled")
        }.build()
        val tool = KimiWebTool(OfficialToolIds.KIMI_SEARCH, KimiWebToolApi("key", client))
        val work = async(Dispatchers.Default) { tool.execute(context, mapOf("text_query" to "q")) }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            work.cancelAndJoin()
            assertTrue(requireNotNull(call).isCanceled())
            try { work.await(); error("Cancellation should propagate") } catch (_: CancellationException) { }
        } finally {
            released.countDown()
        }
    }

    private companion object {
        val operations = mapOf(OfficialToolIds.KIMI_SEARCH to "search", OfficialToolIds.KIMI_SEARCH_PRO to "search_pro", OfficialToolIds.KIMI_FETCH to "fetch")
        const val SEARCH_BODY = """{"search_results":[{"title":"Page","url":"https://example.com","authority":"S","text":"正文","chunks":[{"text":"片段","score":1.5}]}]}"""
        const val FETCH_BODY = """{"url":"https://example.com/page","title":"Page","markdown":"# Content"}"""
    }
}
