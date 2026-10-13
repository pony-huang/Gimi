package dev.e2b.sdk

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SandboxTest {
    private val api = MockWebServer()
    private val envd = MockWebServer()
    private lateinit var client: E2BClient
    private lateinit var config: E2BConfig

    @Before fun setup() {
        api.start(); envd.start()
        config = E2BConfig.builder().apiKey("control-secret").apiUrl(api.url("/").toString())
            .sandboxUrl(envd.url("/").toString()).build()
        client = E2BClient(config)
    }
    @After fun cleanup() { client.close(); api.shutdown(); envd.shutdown() }
    private fun response(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)
    private fun create(version: String = "0.5.7"): Sandbox {
        api.enqueue(response("""{"sandboxID":"sb-1","envdVersion":"$version","envdAccessToken":"envd-secret","domain":"e2b.app"}"""))
        return client.create()
    }
    private fun info(id: String = "sb-1") = """{"sandboxID":"$id","templateID":"base","state":"running","cpuCount":2,"memoryMB":512,"diskSizeMB":2048,"metadata":{"purpose":"test"},"newField":true}"""

    @Test fun `create uses current endpoint and official wire names`() {
        api.enqueue(response("""{"sandboxID":"sb-1","envdVersion":"0.5.7","envdAccessToken":"envd-secret","domain":null}"""))
        val sandbox = client.create(CreateOptions("custom", 600, mapOf("owner" to "one"), mapOf("HELLO" to "world"), false, true))
        val request = api.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/v2/sandboxes", request.path)
        assertEquals("control-secret", request.getHeader("X-API-Key"))
        val body = Json.obj(Json.parse(request.body.readUtf8()))
        assertEquals("custom", body.text("templateID"))
        assertEquals(600, body.number("timeout").toInt())
        assertFalse(body.get("allow_internet_access").asBoolean)
        assertFalse(body.has("allowInternetAccess"))
        assertEquals("world", body.getAsJsonObject("envVars").text("HELLO"))
        assertEquals("8080-sb-1.e2b.app", sandbox.getHost(8080))
    }

    @Test fun `connect uses v2 and does not reset timeout implicitly`() {
        api.enqueue(response("""{"sandboxID":"sb-1","envdVersion":"0.5.7","envdAccessToken":"envd-secret"}"""))
        client.connect("sb-1")
        val request = api.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/v2/sandboxes/sb-1/connect", request.path)
        assertEquals("{}", request.body.readUtf8())
    }

    @Test fun `lifecycle distinguishes not found and already paused`() {
        val sandbox = create()
        api.takeRequest()
        api.enqueue(response(info()))
        assertTrue(sandbox.isRunning())
        api.enqueue(response("", 404))
        assertFalse(sandbox.isRunning())
        api.enqueue(response("", 409))
        assertFalse(sandbox.pause())
        api.enqueue(response("", 404))
        assertFalse(sandbox.kill())
        api.enqueue(response("", 204))
        sandbox.setTimeout(900)
        repeat(4) { api.takeRequest() }
        val timeout = api.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/sandboxes/sb-1/timeout", timeout.path)
        assertEquals(900, Json.obj(Json.parse(timeout.body.readUtf8())).number("timeout").toInt())
    }

    @Test fun `list preserves tokens and filters across pages`() {
        api.enqueue(response("[${info()}]").setHeader("X-Next-Token", "token+/="))
        val page = client.list(ListOptions(10, states = listOf("running", "paused"), metadata = mapOf("标签" to "a&b")))
        assertTrue(page.hasNext)
        assertEquals("test", page.items.single().metadata["purpose"])
        val first = api.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("running,paused", first.requestUrl!!.queryParameter("state"))
        assertEquals("%25E6%25A0%2587%25E7%25AD%25BE=a%2526b", first.requestUrl!!.queryParameter("metadata"))
        api.enqueue(response("[]"))
        assertFalse(client.list(ListOptions(nextToken = page.nextToken)).hasNext)
        assertEquals("token+/=", api.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("nextToken"))
    }

    @Test fun `rate limiting exposes retry hint without echoing credentials`() {
        api.enqueue(response("""{"message":"control-secret","code":429}""", 429).setHeader("Retry-After", "15"))
        val error = assertThrows(RateLimitException::class.java) { client.create() }
        assertEquals("15", error.retryAfter)
        assertFalse(error.toString().contains("control-secret"))
    }

    @Test fun `redirect cannot forward control credentials`() {
        api.enqueue(MockResponse().setResponseCode(307).setHeader("Location", envd.url("/stolen")))
        assertThrows(E2BException::class.java) { client.create() }
        assertEquals(0, envd.requestCount)
        assertEquals(1, api.requestCount)
    }

    @Test fun `client close releases local resources without killing sandbox`() {
        create()
        client.close()
        assertEquals(1, api.requestCount)
        assertThrows(IllegalStateException::class.java) { client.list() }
    }

    @Test fun `sandbox close destroys sandbox`() {
        val sandbox = create()
        api.takeRequest()
        api.enqueue(response("", 204))
        sandbox.close()
        assertEquals("DELETE", api.takeRequest(1, TimeUnit.SECONDS)!!.method)
    }

    @Test fun `file operations keep control key out of envd and encode paths`() {
        val sandbox = create()
        envd.enqueue(response("[ {\"name\":\"a.txt\",\"path\":\"/tmp/a & b.txt\",\"type\":\"file\"} ]"))
        sandbox.files.write("/tmp/a & b.txt", byteArrayOf(0, 1, -1), "root")
        val upload = envd.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/tmp/a & b.txt", upload.requestUrl!!.queryParameter("path"))
        assertEquals("root", upload.requestUrl!!.queryParameter("username"))
        assertEquals("envd-secret", upload.getHeader("X-Access-Token"))
        assertNull(upload.getHeader("X-API-Key"))
        assertNull(upload.getHeader("Authorization"))
        assertEquals("sb-1", upload.getHeader("E2b-Sandbox-Id"))
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(upload.body.readByteArray().toList().windowed(3).contains(listOf<Byte>(0, 1, -1)))
        envd.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(0, -1))))
        assertArrayEquals(byteArrayOf(0, -1), sandbox.files.readBytes("/tmp/a & b.txt"))
        assertEquals("GET", envd.takeRequest(1, TimeUnit.SECONDS)!!.method)
    }

    @Test fun `filesystem handles proto int64 fields and absence correctly`() {
        val sandbox = create()
        envd.enqueue(response("""{"entry":{"name":"a","path":"/tmp/a","type":"FILE_TYPE_FILE","size":"9223372036854775806","mode":420,"modifiedTime":"2026-10-11T00:00:00Z","future":"ignored"}}"""))
        assertEquals(Long.MAX_VALUE - 1, sandbox.files.getInfo("/tmp/a").size)
        val stat = envd.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/filesystem.Filesystem/Stat", stat.path)
        assertEquals("1", stat.getHeader("Connect-Protocol-Version"))
        assertNull(stat.getHeader("Authorization"))
        envd.enqueue(response("""{"code":"not_found"}""", 404))
        assertFalse(sandbox.files.exists("/missing"))
        envd.enqueue(response("""{"code":"permission_denied"}""", 403))
        assertThrows(AuthenticationException::class.java) { sandbox.files.exists("/restricted") }
        envd.enqueue(response("""{"code":"already_exists"}""", 409))
        assertFalse(sandbox.files.makeDir("/tmp"))
    }

    @Test fun `legacy user is encoded separately from access token`() {
        val sandbox = create("0.3.9")
        envd.enqueue(response("{}"))
        sandbox.files.list("/tmp")
        val request = envd.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Basic dXNlcjo=", request.getHeader("Authorization"))
        assertEquals("envd-secret", request.getHeader("X-Access-Token"))
    }

    @Test fun `network failures are Java-friendly SDK exceptions without replay`() {
        api.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        assertThrows(TransportException::class.java) { client.create() }
        assertEquals(1, api.requestCount)
    }

    @Test fun `default routing matches official shared domain`() {
        val http = HttpTransport(E2BConfig.builder().apiKey("key").build())
        try {
            val env = Envd(http, E2BConfig.builder().apiKey("key").build(), "sb", "e2b.app", "token", "0.5.7")
            val request = env.request(listOf("filesystem.Filesystem", "Stat")).build()
            assertEquals("sandbox.e2b.app", request.url.host)
            assertEquals("49983", request.header("E2b-Sandbox-Port"))
            assertNull(request.header("X-API-Key"))
        } finally { http.close() }
    }
}
