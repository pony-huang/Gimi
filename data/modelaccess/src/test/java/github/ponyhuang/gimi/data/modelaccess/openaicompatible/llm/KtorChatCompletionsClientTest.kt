package github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm

import com.google.adk.kt.types.HttpOptions
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Chat Completions 传输层（Ktor/OkHttp）：SSE 分行与跨分块断行、`[DONE]` 终止、
 * 非 2xx 错误归类与重试、HTTP 200 错误载荷、鉴权头与端点拼接、取消关闭连接。
 */
class KtorChatCompletionsClientTest {
  private lateinit var server: MockWebServer
  private lateinit var client: KtorChatCompletionsClient

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    client =
        KtorChatCompletionsClient(
            headersProvider = { mapOf("Authorization" to "Bearer test-key") },
            httpOptions = HttpOptions(baseUrl = server.url("/v1").toString()),
            // 重试零延迟：只验证重试次数，不让用例等真实退避。
            retryDelay = { _, _ -> 0.milliseconds },
        )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun request() =
      ChatCompletionRequest(
          model = "test-model",
          messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi"))),
      )

  @Test
  fun createDecodesFullResponseAndSendsAuthHeader() = runBlocking {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"model":"test-model","choices":[{"message":{"role":"assistant","content":"hello"}}]}"""),
    )
    val response = client.create(request())
    assertEquals("hello", response.choices.single().message?.contentText())
    val recorded = server.takeRequest()
    assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
    assertEquals("/v1/chat/completions", recorded.path)
  }

  @Test
  fun http200WithErrorBodyThrowsApiException() = runBlocking {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"error":{"type":"insufficient_quota"}}"""),
    )
    try {
      client.create(request())
      fail("expected ChatCompletionsApiException")
    } catch (e: ChatCompletionsApiException) {
      // 错误消息只含类型，不含响应体原文。
      assertTrue(e.message!!.contains("insufficient_quota"))
    }
  }

  @Test
  fun retryableStatusRetriesThenFails() = runBlocking {
    repeat(3) {
      server.enqueue(
          MockResponse().setResponseCode(500).setBody("""{"error":{"type":"server_error"}}"""),
      )
    }
    try {
      client.create(request())
      fail("expected ChatCompletionsApiException")
    } catch (e: ChatCompletionsApiException) {
      assertEquals(500, e.statusCode)
    }
    // 首次 + MAX_RETRIES=2 次重试。
    assertEquals(3, server.requestCount)
  }

  @Test
  fun streamEmitsSplitSseLinesAndStopsAtDone() = runBlocking {
    val body =
        buildString {
          append("data: {\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\n")
          append("data: {\"choices\":[{\"delta\":{\"content\":\"llo\"},\"finish_reason\":\"stop\"}]}\n\n")
          append("data: [DONE]\n\n")
          append("data: {\"should-not-arrive\":true}\n\n")
        }
    // 按小节流式下发：data 行被拆进多个分块，覆盖 readLine 的跨块断行处理。
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
            .throttleBody(5, 1, TimeUnit.MILLISECONDS),
    )
    val chunks = withTimeout(10_000) { client.createStream(request()).toList() }
    assertEquals(
        listOf("he", "llo"),
        chunks.mapNotNull { it.choices.firstOrNull()?.delta?.contentText() },
    )
    // [DONE] 之后的事件不得到达。
    assertTrue(chunks.all { it.error == null && it.choices.isNotEmpty() })
  }

  @Test
  fun streamEmitsErrorPayloadAsResponseChunk() = runBlocking {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(
                "data: {\"error\":{\"type\":\"context_length_exceeded\"}}\n" +
                    "\ndata: [DONE]\n\n",
            ),
    )
    // 流内错误载荷按事件透传给上层（由 ADK 转换层决定失败语义），传输层不吞不掉。
    val chunks = withTimeout(10_000) { client.createStream(request()).toList() }
    assertEquals("context_length_exceeded", chunks.single().error?.type)
  }

  @Test
  fun cancellingStreamClosesExchange() = runBlocking {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n\n" + "x".repeat(64))
            .throttleBody(4, 50, TimeUnit.MILLISECONDS),
    )
    withTimeout(10_000) {
      val job = launch {
        client.createStream(request()).collect { delay(50) }
      }
      delay(150)
      // 取消收集后 flow 必须终止：底层连接随协程取消关闭，不悬挂到读超时。
      job.cancelAndJoin()
    }
    Unit
  }
}
