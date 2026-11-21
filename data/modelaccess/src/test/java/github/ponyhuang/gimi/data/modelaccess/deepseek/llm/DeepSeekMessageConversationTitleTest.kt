package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.annotations.FrameworkInternalApi
import github.ponyhuang.gimi.domain.modelaccess.CONVERSATION_TITLE_METADATA_KEY
import github.ponyhuang.gimi.domain.modelaccess.DEFERRED_CATALOG
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Tool
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DeepSeek 会话标题（title 扩展字段）的开关与响应解析契约：
 * 默认关闭不注入；开启后首轮注入、流式取最后一个 message_delta 的 delta.title、
 * 非流式取顶层 title，均写入最终 LlmResponse.customMetadata 透传给标题插件；
 * 请求声明了 DeepSeek 保留名的自定义工具时跳过注入（与 title 互斥，整请求会 400）。
 */
class DeepSeekMessageConversationTitleTest {

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

    @Test
    fun disabledByDefaultDoesNotRequestTitle() {
        val responses = generate(stream = false, body = MESSAGE_WITHOUT_TITLE, titleEnabled = false)
        assertFalse(CONVERSATION_TITLE_METADATA_KEY in responses.single().customMetadata.orEmpty())
        assertFalse("默认关闭不注入 title", org.json.JSONObject(requestBody()).has("title"))
    }

    @Test
    fun streamingTitleArrivesOnTheFinalAggregateViaCustomMetadata() {
        val responses = generate(stream = true, body = STREAM_WITH_TITLE, titleEnabled = true)

        val aggregate = responses.last()
        assertEquals("北京明天天气", aggregate.customMetadata?.get(CONVERSATION_TITLE_METADATA_KEY))
        // 增量片段不携带标题，标题只出现在最终聚合上。
        assertTrue(responses.dropLast(1).all {
            CONVERSATION_TITLE_METADATA_KEY !in it.customMetadata.orEmpty()
        })
        // 首轮请求注入了 title 参数。
        assertTrue(requestBody().contains("\"title\""))
    }

    @Test
    fun streamingWithoutTitleLeavesCustomMetadataEmpty() {
        val responses = generate(stream = true, body = STREAM_WITHOUT_TITLE, titleEnabled = true)
        assertFalse(CONVERSATION_TITLE_METADATA_KEY in responses.last().customMetadata.orEmpty())
    }

    @Test
    fun nonStreamingTopLevelTitleArrivesViaCustomMetadata() {
        val responses = generate(stream = false, body = MESSAGE_WITH_TITLE, titleEnabled = true)
        assertEquals("北京明天天气", responses.single().customMetadata?.get(CONVERSATION_TITLE_METADATA_KEY))
    }

    @Test
    fun nonStreamingWithoutTitleLeavesCustomMetadataEmpty() {
        val responses = generate(stream = false, body = MESSAGE_WITHOUT_TITLE, titleEnabled = true)
        assertFalse(CONVERSATION_TITLE_METADATA_KEY in responses.single().customMetadata.orEmpty())
    }

    @Test
    fun reservedActiveToolNameSuppressesTitleInjection() {
        // 声明了 DeepSeek 保留名的自定义工具时，title 会与它冲突并让整请求 400。
        val request = requestWithTools(Tool(functionDeclarations = listOf(
            FunctionDeclaration(name = "deepseek_web_search", description = "Search"),
        )))
        generate(stream = false, body = MESSAGE_WITHOUT_TITLE, titleEnabled = true, request = request)
        assertFalse("保留名自定义工具与 title 互斥，不应注入",
            org.json.JSONObject(requestBody()).has("title"))
    }

    @Test
    @OptIn(FrameworkInternalApi::class)
    fun reservedDeferredCatalogNameSuppressesTitleInjection() {
        // defer_loading 目录工具同样进入顶层 tools 数组，保留名同样触发 400。
        val snapshot = adkJson.encodeToString(FunctionDeclaration.serializer(),
            FunctionDeclaration(name = "deepseek_read_url", description = "Fetch"))
        val request = requestWithTools(systemInstruction = Content(role = Role.SYSTEM, parts = listOf(
            Part(partMetadata = mapOf(DEFERRED_CATALOG to listOf(snapshot))),
        )))
        generate(stream = false, body = MESSAGE_WITHOUT_TITLE, titleEnabled = true, request = request)
        assertFalse("defer_loading 保留名同样互斥，不应注入",
            org.json.JSONObject(requestBody()).has("title"))
    }

    @Test
    fun ordinaryToolNameKeepsTitleInjection() {
        val request = requestWithTools(Tool(functionDeclarations = listOf(
            FunctionDeclaration(name = "clock", description = "Read clock"),
        )))
        generate(stream = false, body = MESSAGE_WITH_TITLE, titleEnabled = true, request = request)
        assertTrue(org.json.JSONObject(requestBody()).has("title"))
    }

    private var lastRequestBody: String? = null

    private fun requestBody(): String =
        lastRequestBody ?: error("No request captured")

    private fun defaultRequest(): LlmRequest = LlmRequest(contents = listOf(
        Content(role = Role.USER, parts = listOf(Part(text = "北京明天会下雨吗"))),
    ))

    /** 带工具声明的首轮请求；[systemInstruction] 非空时同时附加（用于 defer_loading 目录用例）。 */
    @OptIn(FrameworkInternalApi::class)
    private fun requestWithTools(
        vararg tools: Tool,
        systemInstruction: Content? = null,
    ): LlmRequest = defaultRequest().copy(config = GenerateContentConfig(
        tools = tools.toList(),
        systemInstruction = systemInstruction,
    ))

    private fun generate(
        stream: Boolean,
        body: String,
        titleEnabled: Boolean,
        request: LlmRequest = defaultRequest(),
    ): List<LlmResponse> {
        val client = AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(server.url("/").toString())
            .build()
        val model = DeepSeekMessageChat(
            name = "deepseek-flash",
            client = client,
            conversationTitleEnabled = titleEnabled,
        )
        val contentType = if (stream) "text/event-stream" else "application/json"
        server.enqueue(MockResponse().setHeader("Content-Type", contentType).setBody(body))
        try {
            val responses = runBlocking { model.generateContent(request, stream = stream).toList() }
            assertFalse("响应不应是错误：${responses.map { it.errorMessage }}",
                responses.all { it.errorMessage != null })
            lastRequestBody = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)?.body?.readUtf8()
            return responses
        } finally {
            client.close()
        }
    }

    private companion object {
        val MESSAGE_WITHOUT_TITLE: String = """
            {
              "id": "msg_test",
              "type": "message",
              "role": "assistant",
              "model": "deepseek-flash",
              "content": [{"type": "text", "text": "ok"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
        """.trimIndent()

        val MESSAGE_WITH_TITLE: String = """
            {
              "id": "msg_test",
              "type": "message",
              "role": "assistant",
              "model": "deepseek-flash",
              "title": "北京明天天气",
              "content": [{"type": "text", "text": "ok"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
        """.trimIndent()

        private fun start() =
            """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"deepseek-flash","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}}"""

        private fun blockStart() =
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""

        private fun blockDelta() =
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}"""

        private fun blockStop() =
            """{"type":"content_block_stop","index":0}"""

        private fun finish(withTitle: Boolean) =
            if (withTitle) {
                // DeepSeek 扩展：标题随最后一个 message_delta 的 delta.title 下发（SDK 无此字段，落 additionalProperties）。
                """{"type":"message_delta","delta":{"stop_reason":"end_turn","title":"北京明天天气"},"usage":{"output_tokens":1}}"""
            } else {
                """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}"""
            }

        private fun sse(vararg events: String): String = events.joinToString("") { event ->
            "event: ${org.json.JSONObject(event).getString("type")}\ndata: $event\n\n"
        }

        val STREAM_WITHOUT_TITLE: String = sse(start(), blockStart(), blockDelta(), blockStop(), finish(false), """{"type":"message_stop"}""")

        val STREAM_WITH_TITLE: String = sse(start(), blockStart(), blockDelta(), blockStop(), finish(true), """{"type":"message_stop"}""")
    }
}
