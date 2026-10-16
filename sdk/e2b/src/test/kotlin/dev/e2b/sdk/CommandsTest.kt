package dev.e2b.sdk

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class CommandsTest {
    private val server = MockWebServer()
    private lateinit var client: E2BClient
    private lateinit var sandbox: Sandbox
    @Before fun setup() {
        server.start()
        client = E2BClient(E2BConfig.builder().apiKey("api-key").apiUrl(server.url("/").toString()).sandboxUrl(server.url("/").toString()).build())
        server.enqueue(MockResponse().setBody("""{"sandboxID":"sb","envdVersion":"0.5.7","envdAccessToken":"token"}"""))
        sandbox = client.create()
        server.takeRequest()
    }
    @After fun cleanup() { client.close(); server.shutdown() }
    private fun frame(json: String, flags: Int = 0): Buffer = Buffer().writeByte(flags).writeInt(json.toByteArray().size).writeUtf8(json)
    private fun start() = """{"event":{"start":{"pid":123}}}"""
    private fun end(code: Int = 0) = """{"event":{"end":{"exitCode":$code,"exited":true}}}"""
    private fun data(bytes: ByteArray, channel: String = "stdout") = """{"event":{"data":{"$channel":"${Base64.getEncoder().encodeToString(bytes)}"}}}"""
    private fun stream(vararg frames: Buffer) {
        val body = Buffer()
        frames.forEach { body.write(it, it.size) }
        server.enqueue(MockResponse().setHeader("Content-Type", "application/connect+json").setBody(body))
    }

    @Test fun `commands follow Connect framing and preserve split UTF8`() {
        val utf8 = "你好".toByteArray()
        stream(frame(start()), frame(data(utf8.copyOfRange(0, 2))), frame("""{"event":{"keepalive":{}}}"""), frame(data(utf8.copyOfRange(2, utf8.size))), frame(end()), frame("{}", 2))
        val result = sandbox.commands.run("echo 你好", CommandOptions(cwd = "/tmp", envs = mapOf("A" to "B")))
        assertEquals("你好", result.stdout)
        assertEquals(0, result.exitCode)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/process.Process/Start", request.path)
        assertEquals("application/connect+json", request.getHeader("Content-Type"))
        assertEquals("60000", request.getHeader("Connect-Timeout-Ms"))
        assertNull(request.getHeader("X-API-Key"))
        assertEquals(0, request.body.readByte().toInt())
        val length = request.body.readInt()
        val body = Json.obj(Json.parse(request.body.readUtf8(length.toLong())))
        assertFalse(body.get("stdin").asBoolean)
        val process = body.getAsJsonObject("process")
        assertEquals("/bin/bash", process.text("cmd"))
        assertEquals(listOf("-l", "-c", "echo 你好"), process.getAsJsonArray("args").map { it.asString })
        assertEquals("/tmp", process.text("cwd"))
    }

    @Test fun `nonzero exit includes captured output`() {
        stream(frame(start()), frame(data("failure".toByteArray(), "stderr")), frame(end(2)), frame("{}", 2))
        val error = assertThrows(CommandExitException::class.java) { sandbox.commands.run("exit 2") }
        assertEquals(2, error.result.exitCode)
        assertEquals("failure", error.result.stderr)
    }

    @Test fun `terminal RPC error is not hidden by earlier exit`() {
        stream(frame(start()), frame(end()), frame("""{"error":{"code":"permission_denied","message":"secret"}}""", 2))
        val error = assertThrows(AuthenticationException::class.java) { sandbox.commands.run("true") }
        assertFalse(error.toString().contains("secret"))
    }

    @Test fun `truncated payload is rejected`() {
        stream(frame(start()), Buffer().writeByte(0).writeInt(100).writeUtf8("{}"))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true") }
    }

    @Test fun `missing terminal frame is rejected`() {
        stream(frame(start()), frame(end()))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true") }
    }

    @Test fun `missing process exit is rejected`() {
        stream(frame(start()), frame("{}", 2))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true") }
    }

    @Test fun `oversized output is bounded`() {
        stream(frame(start()), frame(data(ByteArray(10))), frame(end()), frame("{}", 2))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true", CommandOptions(maxOutputBytes = 5)) }
    }

    @Test fun `oversized frame is rejected before allocation`() {
        stream(frame(start()), Buffer().writeByte(0).writeInt(16 * 1024 * 1024 + 1))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true") }
    }

    @Test fun `compressed frame is rejected`() {
        stream(frame(start()), frame("{}", 1))
        assertThrows(ProtocolException::class.java) { sandbox.commands.run("true") }
    }

    @Test fun `background handle supports input and remote termination`() {
        stream(frame(start()), frame(end()), frame("{}", 2))
        sandbox.commands.start("cat", CommandOptions(stdin = true, user = "root")).use { handle ->
            assertEquals(123, handle.pid)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("{}"))
            handle.sendStdin("hello")
            val input = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/process.Process/SendInput", input.path)
            assertEquals("Basic cm9vdDo=", input.getHeader("Authorization"))
            val body = Json.obj(Json.parse(input.body.readUtf8()))
            assertEquals("aGVsbG8=", body.getAsJsonObject("input").text("stdin"))
            server.enqueue(MockResponse().setBody("{}"))
            handle.closeStdin()
            assertEquals("/process.Process/CloseStdin", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(handle.kill())
            val signal = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("SIGNAL_SIGKILL", Json.obj(Json.parse(signal.body.readUtf8())).text("signal"))
            assertEquals(0, handle.await().exitCode)
            assertEquals(0, handle.await().exitCode)
        }
    }

    @Test fun `closing handle does not send a remote kill`() {
        stream(frame(start()), frame(end()), frame("{}", 2))
        sandbox.commands.start("true").close()
        assertEquals(2, server.requestCount)
    }

    @Test fun `connect uses process selector and can disable stream timeout`() {
        stream(frame(start()), frame(end()), frame("{}", 2))
        sandbox.commands.connect(123, CommandOptions(timeoutSeconds = 0)).use { assertEquals(123, it.await().pid) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/process.Process/Connect", request.path)
        assertNull(request.getHeader("Connect-Timeout-Ms"))
    }
}
