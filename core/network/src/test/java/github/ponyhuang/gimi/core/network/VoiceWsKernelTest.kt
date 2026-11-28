package github.ponyhuang.gimi.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 握手被拒时，服务端响应详情（状态码、关键响应头、错误体）必须完整上抛，否则排查无从下手。 */
class VoiceWsKernelTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test
    fun handshakeFailureCarriesServerResponseDetail() = runBlocking {
        val body = """{"code":"InvalidApiKey","message":"Invalid API-key provided."}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("x-request-id", "req-123456")
                .setBody(body),
        )

        val received = mutableListOf<Pair<Int, String?>>()
        val kernel = VoiceWsKernel(
            client = client,
            request = Request.Builder().url(server.url("/ws")).build(),
            handshakeFailure = { statusCode, _, detail, _ ->
                received += statusCode to detail
                IllegalStateException("mapped $statusCode")
            },
        )

        val frame = withTimeout(5_000) {
            var last = kernel.frames().receive()
            while (last !is VoiceWsKernel.WsFrame.Failure) last = kernel.frames().receive()
            last
        }
        val failure = frame.error
        assertEquals(1, received.size)
        assertEquals(401, received[0].first)
        assertNotNull(received[0].second)
        val detail = received[0].second.orEmpty()
        assertTrue("detail 应含响应体: $detail", detail.contains("InvalidApiKey"))
        assertTrue("detail 应含 request-id: $detail", detail.contains("req-123456"))
        assertTrue("detail 应含状态码前缀: $detail", detail.contains("headers=["))
        assertEquals("mapped 401", failure.message)
        kernel.close()
    }

    @Test
    fun transportFailureWithoutResponseSkipsHandshakeMapping() = runBlocking {
        // 本地无服务监听，连接直接失败且没有 HTTP 响应：应原样上抛异常而非走握手映射。
        val kernel = VoiceWsKernel(
            client = client,
            request = Request.Builder().url("http://127.0.0.1:1/ws").build(),
            handshakeFailure = { _, _, _, _ -> throw AssertionError("无响应的传输失败不应触发握手映射") },
        )
        val frame = withTimeout(10_000) {
            var last = kernel.frames().receive()
            while (last !is VoiceWsKernel.WsFrame.Failure) last = kernel.frames().receive()
            last
        }
        assertTrue("无响应的传输失败应原样上抛 IOException: ${frame.error}", frame.error is java.io.IOException)
        kernel.close()
    }

    @Test
    fun handshakeDetailTruncatesOversizedBody() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("x".repeat(64 * 1024)),
        )
        var detail: String? = null
        val kernel = VoiceWsKernel(
            client = client,
            request = Request.Builder().url(server.url("/ws")).build(),
            handshakeFailure = { _, _, d, _ -> detail = d; IllegalStateException("mapped") },
        )
        withTimeout(5_000) {
            var last = kernel.frames().receive()
            while (last !is VoiceWsKernel.WsFrame.Failure) last = kernel.frames().receive()
        }
        assertNotNull(detail)
        assertTrue(detail!!.length < 16 * 1024)
        kernel.close()
    }

    @Test
    fun sendControlAfterHandshakeFailureDoesNotPreemptTypedFailure() = runBlocking {
        // 回归：握手被拒后 socket.send() 返回 false，发送路径不得抢先抛普通 IOException——
        // 高负载下握手失败可先于发送协程执行，抢先抛错会把 frames 通道里已就绪的
        // 类型化握手失败（401→认证类）掩盖成网络错误，让用户看到错误的失败分类。
        server.enqueue(MockResponse().setResponseCode(401))
        val kernel = VoiceWsKernel(
            client = client,
            request = Request.Builder().url(server.url("/ws")).build(),
            handshakeFailure = { statusCode, _, _, _ -> IllegalStateException("mapped $statusCode") },
        )

        val failure = withTimeout(5_000) {
            var last = kernel.frames().receive()
            while (last !is VoiceWsKernel.WsFrame.Failure) last = kernel.frames().receive()
            last
        }
        assertEquals("mapped 401", failure.error.message)

        // 连接已终局（此时 send 必然返回 false）：发送不再抛错，失败报告由 frames 负责。
        kernel.sendControl(com.google.gson.JsonObject().apply { addProperty("probe", true) })
        kernel.close()
    }
}
