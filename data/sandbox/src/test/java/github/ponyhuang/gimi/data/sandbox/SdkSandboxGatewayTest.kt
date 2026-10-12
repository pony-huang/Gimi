package github.ponyhuang.gimi.data.sandbox

import dev.e2b.sdk.E2BClient
import dev.e2b.sdk.E2BConfig
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SdkSandboxGatewayTest {
    private val api = MockWebServer()
    private val env = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: E2BClient
    private lateinit var gateway: SdkSandboxGateway
    @Before fun setup() = runBlocking {
        api.start(); env.start()
        client = E2BClient(E2BConfig.builder().apiKey("control-key").apiUrl(api.url("/").toString())
            .sandboxUrl(env.url("/").toString()).codeInterpreterUrl(env.url("/").toString()).build())
        gateway = SdkSandboxGateway(SandboxConnection(apiKey = "control-key", envs = mapOf("EMPTY" to "")), scope, client)
        api.enqueue(MockResponse().setBody("""{"sandboxID":"sb","domain":"e2b.app","envdVersion":"0.5.7","envdAccessToken":"envd-token"}"""))
        gateway.create()
    }
    @After fun cleanup() { client.close(); scope.cancel(); api.shutdown(); env.shutdown() }

    @Test fun nonzeroCommandExitIsReturnedAsCompletedOutput() = runBlocking {
        fun frame(message: String, flags: Int = 0) = Buffer().writeByte(flags).writeInt(message.toByteArray().size).writeUtf8(message)
        val stdout = Base64.getEncoder().encodeToString("before".toByteArray())
        val body = Buffer()
        listOf(frame("""{"event":{"start":{"pid":123}}}"""), frame("""{"event":{"data":{"stdout":"$stdout"}}}"""),
            frame("""{"event":{"end":{"exitCode":5}}}"""), frame("{}", 2)).forEach { body.write(it, it.size) }
        env.enqueue(MockResponse().setHeader("Content-Type", "application/connect+json").setBody(body))
        val result = gateway.execute(SandboxOperation.Command("exit 5")) as SandboxOutput.Command
        assertEquals(5, result.exitCode)
        assertEquals("before", result.stdout)
    }
    @Test fun kernelErrorIsReturnedWithoutThrowingGatewayFailure() = runBlocking {
        env.enqueue(MockResponse().setBody("""{"type":"error","name":"ValueError","value":"bad","traceback":"line 1"}
"""))
        val result = gateway.execute(SandboxOperation.Code("raise ValueError('bad')")) as SandboxOutput.Code
        assertEquals("ValueError: bad\nline 1", result.error)
    }
    @Test fun emptyTextCanBeWrittenAndCreatedEnvironmentVariablesAreForwarded() = runBlocking {
        env.enqueue(MockResponse().setBody("""[{"name":"empty","path":"empty","type":"FILE_TYPE_FILE"}]"""))
        assertEquals(SandboxOutput.WrittenFile("empty"), gateway.execute(SandboxOperation.WriteFile("empty", "")))
        val createRequest = api.takeRequest()
        assertTrue(createRequest.body.readUtf8().contains("\"envVars\":{\"EMPTY\":\"\"}"))
        val write = env.takeRequest()
        assertNull(write.getHeader("X-API-Key"))
        assertEquals("envd-token", write.getHeader("X-Access-Token"))
    }
    @Test fun oversizedWriteIsRejectedBeforeNetworkRequest() = runBlocking {
        assertFailure(SandboxGatewayFailure.TOO_LARGE) { gateway.execute(SandboxOperation.WriteFile("large", "你".repeat(349526))) }
        assertEquals(0, env.requestCount)
    }
    @Test fun oversizedReadAndInvalidUtf8AreRejected() = runBlocking {
        env.enqueue(MockResponse().setBody("x".repeat(1024 * 1024 + 1)))
        assertFailure(SandboxGatewayFailure.TOO_LARGE) { gateway.execute(SandboxOperation.ReadFile("large")) }
        env.enqueue(MockResponse().setBody(Buffer().writeByte(255)))
        assertFailure(SandboxGatewayFailure.TEXT_ENCODING) { gateway.execute(SandboxOperation.ReadFile("binary")) }
    }
    @Test fun missingInterpreterHasActionableFailureAndDoesNotEchoServerBody() = runBlocking {
        env.enqueue(MockResponse().setResponseCode(404).setBody("secret diagnostic"))
        assertFailure(SandboxGatewayFailure.INTERPRETER) { gateway.execute(SandboxOperation.Code("1")) }
    }
    @Test fun authenticationAndMissingFilesAreMappedSeparately() = runBlocking {
        env.enqueue(MockResponse().setResponseCode(401).setBody("secret diagnostic"))
        assertFailure(SandboxGatewayFailure.AUTHENTICATION) { gateway.execute(SandboxOperation.ReadFile("file")) }
        env.enqueue(MockResponse().setResponseCode(404))
        assertFailure(SandboxGatewayFailure.FILE_NOT_FOUND) { gateway.execute(SandboxOperation.ReadFile("file")) }
    }
    @Test fun directoryTypesAreNormalizedToPluginContract() = runBlocking {
        env.enqueue(MockResponse().setBody("""{"entries":[{"name":"a","path":"a","type":"FILE_TYPE_FILE"},{"name":"b","path":"b","type":"FILE_TYPE_DIRECTORY"},{"name":"c","path":"c"}]}"""))
        val result = gateway.execute(SandboxOperation.ListFiles()) as SandboxOutput.Files
        assertEquals(listOf("file", "dir", "unknown"), result.entries.map { it.type })
    }
    private suspend fun assertFailure(expected: SandboxGatewayFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected gateway failure") }
        catch (failure: SandboxGatewayException) {
            assertEquals(expected, failure.failure)
            assertFalse(failure.message.orEmpty().contains("secret diagnostic"))
        }
    }
}
