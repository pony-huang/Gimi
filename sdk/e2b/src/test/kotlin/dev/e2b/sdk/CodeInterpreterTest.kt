package dev.e2b.sdk

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class CodeInterpreterTest {
    private val api = MockWebServer()
    private val interpreter = MockWebServer()
    private lateinit var client: E2BClient
    private lateinit var sandbox: Sandbox

    @Before fun setup() {
        api.start(); interpreter.start()
        client = E2BClient(E2BConfig.builder().apiKey("control-secret")
            .apiUrl(api.url("/").toString()).codeInterpreterUrl(interpreter.url("/").toString()).build())
        api.enqueue(MockResponse().setBody("""{"sandboxID":"sb-code","domain":"e2b.app","envdVersion":"0.5.7","envdAccessToken":"envd-secret"}"""))
        sandbox = client.create(CreateOptions("code-interpreter-v1"))
    }
    @After fun cleanup() { client.close(); api.shutdown(); interpreter.shutdown() }

    @Test fun `code request uses interpreter routing and never sends control key`() {
        interpreter.enqueue(MockResponse().setBody("""{"type":"stdout","text":"你好\n"}
{"type":"result","text":"display","is_main_result":false}
{"type":"result","text":"2","is_main_result":true}
{"type":"number_of_executions","execution_count":1}
"""))
        val result = sandbox.code.run("1 + 1", CodeOptions(envs = mapOf("HELLO" to "world")))
        assertEquals("你好\n", result.stdout)
        assertEquals("2", result.text)
        assertEquals(1, result.executionCount)
        assertEquals("", result.error)
        val request = interpreter.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/execute", request.path)
        assertEquals("49999", request.getHeader("E2b-Sandbox-Port"))
        assertEquals("sb-code", request.getHeader("E2b-Sandbox-Id"))
        assertEquals("envd-secret", request.getHeader("X-Access-Token"))
        assertNull(request.getHeader("X-API-Key"))
        val json = Json.obj(Json.parse(request.body.readUtf8()))
        assertEquals("python", json.text("language"))
        assertEquals("world", json.getAsJsonObject("env_vars").text("HELLO"))
    }

    @Test fun `kernel error is a completed execution and preserves logs`() {
        interpreter.enqueue(MockResponse().setBody("""{"type":"stdout","text":"before"}
{"type":"stderr","text":"warning"}
{"type":"error","name":"ValueError","value":"bad","traceback":["line 1","line 2"]}
"""))
        val result = sandbox.code.run("throw new Error('bad')", CodeOptions(language = "javascript"))
        assertEquals("before", result.stdout)
        assertEquals("warning", result.stderr)
        assertEquals("ValueError: bad\nline 1\nline 2", result.error)
        assertEquals("", result.text)
    }

    @Test(expected = ProtocolException::class)
    fun `malformed stream is rejected`() {
        interpreter.enqueue(MockResponse().setBody("{not json}\n"))
        sandbox.code.run("1")
    }

    @Test(expected = ProtocolException::class)
    fun `oversized stream is rejected before unbounded buffering`() {
        interpreter.enqueue(MockResponse().setBody("x".repeat(128)))
        sandbox.code.run("1", CodeOptions(maxOutputBytes = 64))
    }

    @Test(expected = AuthenticationException::class)
    fun `authentication failure is not converted to an empty execution`() {
        interpreter.enqueue(MockResponse().setResponseCode(401).setBody("secret body"))
        sandbox.code.run("1")
    }

    @Test fun `stream failure preserves already captured output`() {
        interpreter.enqueue(MockResponse().setBody("""{"type":"stdout","text":"before"}
{invalid json}
"""))
        try { sandbox.code.run("1"); fail("Expected stream failure") }
        catch (failure: PartialOutputException) {
            assertEquals("before", failure.partialOutput)
            assertTrue(failure.failure is ProtocolException)
        }
    }
}
