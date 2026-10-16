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
    @Test fun archiveReadsCanExceedDefaultAssetLimitWithoutChangingDefaultReads() = runBlocking {
        val bytes = ByteArray(1024 * 1024 + 1) { 0xff.toByte() }
        env.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        assertFailure(SandboxGatewayFailure.TOO_LARGE) { gateway.execute(SandboxOperation.ReadBytes("/skill.zip")) }
        env.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val output = gateway.execute(SandboxOperation.ReadBytes("/skill.zip", 20 * 1024 * 1024)) as SandboxOutput.ReadBytes
        assertArrayEquals(bytes, output.content)
    }

    @Test fun archiveLimitRejectsOversizedChunkedResponseDuringRead() = runBlocking {
        env.enqueue(MockResponse().setChunkedBody(Buffer().write(ByteArray(20 * 1024 * 1024 + 1)), 8192))
        assertFailure(SandboxGatewayFailure.TOO_LARGE) {
            gateway.execute(SandboxOperation.ReadBytes("/large.zip", 20 * 1024 * 1024))
        }
    }

    @Test fun invalidBinaryReadBudgetsAreRejected() {
        listOf(0, -1, 20 * 1024 * 1024 + 1).forEach { limit ->
            try {
                SandboxOperation.ReadBytes("/skill.zip", limit)
                fail("Invalid transfer budget must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun environmentReadsRawBytesWithoutUtf8Decoding() = runBlocking {
        val bytes = byteArrayOf(0, -1, 7)
        env.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val output = gateway.execute(SandboxOperation.ReadBytes("/asset.bin")) as SandboxOutput.ReadBytes
        assertArrayEquals(bytes, output.content)
    }

    @Test fun environmentWritesRawBytesAndRejectsOversizedAssets() = runBlocking {
        env.enqueue(MockResponse().setBody("""[{"name":"asset.bin","type":"FILE_TYPE_FILE","path":"/asset.bin"}]"""))
        val bytes = byteArrayOf(0, -1, 7)
        gateway.execute(SandboxOperation.WriteBytes("/asset.bin", bytes))
        assertTrue(env.takeRequest().body.readByteArray().toList().windowed(bytes.size).any { it == bytes.toList() })
        try {
            gateway.execute(SandboxOperation.WriteBytes("/big.bin", ByteArray(1024 * 1024 + 1)))
            fail("Oversized assets must fail before upload")
        } catch (error: SandboxGatewayException) { assertEquals(SandboxGatewayFailure.TOO_LARGE, error.failure) }
    }
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
    @Test fun backgroundStartReturnsPidWithoutWaitingForExitAndForwardsOptions() = runBlocking {
        val event = """{"event":{"start":{"pid":123}}}"""
        // 仅发送 start，不发送退出事件；若误调用 await，工具会失败而非返回 PID。
        val body = Buffer().writeByte(0).writeInt(event.toByteArray().size).writeUtf8(event)
        env.enqueue(MockResponse().setHeader("Content-Type", "application/connect+json").setBody(body))
        val operation = SandboxOperation.BackgroundCommand("server", 3000, "/tmp", mapOf("A" to "B"), 0)
        val result = gateway.execute(operation) as SandboxOutput.BackgroundCommand
        assertEquals(SandboxOutput.BackgroundCommand("server", 123, "https://3000-sb.e2b.app"), result)
        val request = env.takeRequest()
        assertEquals("/process.Process/Start", request.path)
        assertNull(request.getHeader("Connect-Timeout-Ms"))
        assertNull(request.getHeader("X-API-Key"))
        request.body.readByte()
        val payload = request.body.readUtf8(request.body.readInt().toLong())
        assertTrue(payload.contains("\"cwd\":\"/tmp\""))
        assertTrue(payload.contains("\"envs\":{\"A\":\"B\"}"))
        assertEquals(1, env.requestCount)
    }
    @Test fun backgroundWithoutPortUsesDefaultTimeoutAndNoPreviewUrl() = runBlocking {
        val event = """{"event":{"start":{"pid":124}}}"""
        env.enqueue(MockResponse().setHeader("Content-Type", "application/connect+json")
            .setBody(Buffer().writeByte(0).writeInt(event.toByteArray().size).writeUtf8(event)))
        assertEquals(SandboxOutput.BackgroundCommand("sleep 30", 124), gateway.execute(SandboxOperation.BackgroundCommand("sleep 30")))
        assertEquals("60000", env.takeRequest().getHeader("Connect-Timeout-Ms"))
    }
    @Test fun backgroundStartFailureIsMappedWithoutReplayOrServerBody() = runBlocking {
        env.enqueue(MockResponse().setResponseCode(401).setBody("secret diagnostic"))
        assertFailure(SandboxGatewayFailure.AUTHENTICATION) { gateway.execute(SandboxOperation.BackgroundCommand("server")) }
        assertEquals(1, env.requestCount)
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
